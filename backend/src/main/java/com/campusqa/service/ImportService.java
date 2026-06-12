package com.campusqa.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.campusqa.dto.ApiResponse;
import com.campusqa.dto.ImportFaqItem;
import com.campusqa.dto.ImportResult;
import com.campusqa.model.ImportHistory;
import com.campusqa.repository.FaqRepository;
import com.campusqa.repository.ImportHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {

    private static final String DEFAULT_CATEGORY = "未分类";
    private static final String DEFAULT_SOURCE = "批量导入";
    private static final String JSON_IMPORT_TYPE = "FAQ_JSON";
    private static final String CSV_IMPORT_TYPE = "FAQ_CSV";

    private final FaqRepository faqRepository;
    private final ImportHistoryRepository importHistoryRepository;

    public ImportService(FaqRepository faqRepository, ImportHistoryRepository importHistoryRepository) {
        this.faqRepository = faqRepository;
        this.importHistoryRepository = importHistoryRepository;
    }

    @Transactional
    public ApiResponse<ImportResult> importBatchFaq(JsonNode payload) {
        List<ImportFaqItem> items = parseJsonItems(payload);
        ImportResult result = importItems(items);
        saveHistory("JSON请求", JSON_IMPORT_TYPE, result);
        return ApiResponse.success("FAQ 批量导入完成", result);
    }

    @Transactional
    public ApiResponse<ImportResult> importFaqCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            ImportResult result = new ImportResult(0, 1, List.of("CSV 文件不能为空"));
            saveHistory("空文件", CSV_IMPORT_TYPE, result);
            return ApiResponse.failure("CSV 文件不能为空", result);
        }

        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "faq-import.csv";

        try {
            CsvParseResult parseResult = parseCsv(file);
            ImportResult result = importCsvRows(parseResult.rows(), parseResult.failReasons());
            saveHistory(fileName, CSV_IMPORT_TYPE, result);
            return ApiResponse.success("FAQ CSV 导入完成", result);
        } catch (IllegalArgumentException | IOException ex) {
            ImportResult result = new ImportResult(0, 1, List.of(ex.getMessage()));
            saveHistory(fileName, CSV_IMPORT_TYPE, result);
            return ApiResponse.failure("FAQ CSV 导入失败", result);
        }
    }

    public ApiResponse<List<ImportHistory>> listHistory() {
        return ApiResponse.success("查询成功", importHistoryRepository.findRecent());
    }

    private ImportResult importItems(List<ImportFaqItem> items) {
        return importItems(items, List.of());
    }

    private ImportResult importItems(List<ImportFaqItem> items, List<String> initialFailReasons) {
        int successCount = 0;
        List<String> failReasons = new ArrayList<>(initialFailReasons);

        if (items.isEmpty()) {
            if (failReasons.isEmpty()) {
                failReasons.add("没有可导入的数据");
            }
            return new ImportResult(0, failReasons.size(), failReasons);
        }

        for (int i = 0; i < items.size(); i++) {
            ImportFaqItem item = items.get(i);
            int rowNumber = i + 1;
            if (!StringUtils.hasText(item.question()) || !StringUtils.hasText(item.answer())) {
                failReasons.add("第 " + rowNumber + " 条：question 和 answer 不能为空");
                continue;
            }
            if (faqRepository.existsByQuestion(item.question())) {
                failReasons.add("第 " + rowNumber + " 条：问题已存在");
                continue;
            }

            try {
                faqRepository.save(
                        item.question().trim(),
                        item.answer().trim(),
                        normalizeCategory(item.category()),
                        normalizeSource(item.source())
                );
                successCount++;
            } catch (RuntimeException ex) {
                failReasons.add("第 " + rowNumber + " 条：写入失败，" + ex.getMessage());
            }
        }

        return new ImportResult(successCount, failReasons.size(), failReasons);
    }

    private ImportResult importCsvRows(List<CsvFaqRow> rows, List<String> initialFailReasons) {
        int successCount = 0;
        List<String> failReasons = new ArrayList<>(initialFailReasons);

        if (rows.isEmpty()) {
            if (failReasons.isEmpty()) {
                failReasons.add("没有可导入的数据");
            }
            return new ImportResult(0, failReasons.size(), failReasons);
        }

        for (CsvFaqRow row : rows) {
            ImportFaqItem item = row.item();
            if (!StringUtils.hasText(item.question())) {
                failReasons.add("第 " + row.lineNumber() + " 行：question 不能为空");
                continue;
            }
            if (!StringUtils.hasText(item.answer())) {
                failReasons.add("第 " + row.lineNumber() + " 行：answer 不能为空");
                continue;
            }
            if (faqRepository.existsByQuestion(item.question())) {
                failReasons.add("第 " + row.lineNumber() + " 行：问题已存在");
                continue;
            }

            try {
                faqRepository.save(
                        item.question().trim(),
                        item.answer().trim(),
                        normalizeCategory(item.category()),
                        normalizeSource(item.source())
                );
                successCount++;
            } catch (RuntimeException ex) {
                failReasons.add("第 " + row.lineNumber() + " 行：写入失败，" + ex.getMessage());
            }
        }

        return new ImportResult(successCount, failReasons.size(), failReasons);
    }

    private List<ImportFaqItem> parseJsonItems(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return List.of();
        }

        JsonNode itemsNode;
        if (payload.isArray()) {
            itemsNode = payload;
        } else if (payload.has("items") && payload.get("items").isArray()) {
            itemsNode = payload.get("items");
        } else {
            return List.of();
        }

        List<ImportFaqItem> items = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            items.add(new ImportFaqItem(
                    textValue(node, "question"),
                    textValue(node, "answer"),
                    textValue(node, "category"),
                    textValue(node, "source")
            ));
        }
        return items;
    }

    private CsvParseResult parseCsv(MultipartFile file) throws IOException {
        List<CsvFaqRow> rows = new ArrayList<>();
        List<String> failReasons = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV 文件不能为空");
            }

            List<String> headers = parseCsvLine(removeUtf8Bom(headerLine));
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);
            requireColumn(headerIndex, "question");
            requireColumn(headerIndex, "answer");
            requireColumn(headerIndex, "category");
            requireColumn(headerIndex, "source");

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }

                try {
                    List<String> columns = parseCsvLine(line);
                    rows.add(new CsvFaqRow(
                            lineNumber,
                            new ImportFaqItem(
                                    columnValue(columns, headerIndex.get("question")),
                                    columnValue(columns, headerIndex.get("answer")),
                                    columnValue(columns, headerIndex.get("category")),
                                    columnValue(columns, headerIndex.get("source"))
                            )
                    ));
                } catch (IllegalArgumentException ex) {
                    failReasons.add("第 " + lineNumber + " 行：CSV 格式错误，" + ex.getMessage());
                }
            }
        }
        return new CsvParseResult(rows, failReasons);
    }

    // 轻量级 CSV 解析器：支持普通逗号分隔和简单引号转义；暂不支持单元格内换行等复杂 CSV 场景。
    private List<String> parseCsvLine(String line) {
        ArrayList<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString().trim());
        if (inQuotes) {
            throw new IllegalArgumentException("引号未闭合");
        }
        return values;
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        HashMap<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(headers.get(i).trim().toLowerCase(), i);
        }
        return index;
    }

    private void requireColumn(Map<String, Integer> headerIndex, String columnName) {
        if (!headerIndex.containsKey(columnName)) {
            throw new IllegalArgumentException("CSV 缺少必需字段：" + columnName);
        }
    }

    private String columnValue(List<String> columns, Integer index) {
        if (index == null || index < 0 || index >= columns.size()) {
            return null;
        }
        return columns.get(index);
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    private String normalizeCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : DEFAULT_CATEGORY;
    }

    private String normalizeSource(String source) {
        return StringUtils.hasText(source) ? source.trim() : DEFAULT_SOURCE;
    }

    private String removeUtf8Bom(String value) {
        if (value != null && value.startsWith("\uFEFF")) {
            return value.substring(1);
        }
        return value;
    }

    private void saveHistory(String fileName, String importType, ImportResult result) {
        String message = result.failReasons().isEmpty()
                ? "导入完成"
                : String.join("; ", result.failReasons());
        importHistoryRepository.save(
                fileName,
                importType,
                result.successCount(),
                result.failCount(),
                message
        );
    }

    private record CsvParseResult(
            List<CsvFaqRow> rows,
            List<String> failReasons
    ) {
    }

    private record CsvFaqRow(
            int lineNumber,
            ImportFaqItem item
    ) {
    }
}
