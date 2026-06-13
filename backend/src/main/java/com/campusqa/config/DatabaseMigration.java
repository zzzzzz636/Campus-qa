package com.campusqa.config;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移：确保新增字段、表结构兼容旧数据、清洗历史内容中的页面元数据
 */
@Component
public class DatabaseMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigration.class);

    // 页面元数据正则（与 CrawlerService.stripMetadata 保持一致）
    private static final Pattern METADATA_PATTERN = Pattern.compile(
            "\\s*(浏览(次数)?|点击(次数|量)?|访问(量|次数)|来源|作者|供稿|编辑|审核|发布者" +
            "|责任编辑|文字|摄影|图片|发文单位|发布部门|信息员|录入|审核人" +
            "|发布日期|发布时间|更新日期|创建日期|发文时间)" +
            "[：:：\\s]*[^\\s。！？，,;；]*[\\s。！？]*",
            Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addUpdatedAtColumn();
        addViewCountColumn();
        cleanMetadataFromExistingContent();
    }

    private void addUpdatedAtColumn() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE knowledge_doc ADD COLUMN updated_at TEXT");
            jdbcTemplate.execute(
                    "UPDATE knowledge_doc SET updated_at = created_at WHERE updated_at IS NULL");
            log.info("Database migration: added knowledge_doc.updated_at");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("duplicate") || msg.contains("already exists"))) {
                log.debug("Database migration: knowledge_doc.updated_at already exists, skip");
            } else {
                log.warn("Database migration knowledge_doc.updated_at failed: {}", msg);
            }
        }
        addViewCountColumn();
    }

    private void addViewCountColumn() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE knowledge_doc ADD COLUMN view_count INTEGER NOT NULL DEFAULT 0");
            log.info("Database migration: added knowledge_doc.view_count");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("duplicate") || msg.contains("already exists"))) {
                log.debug("Database migration: knowledge_doc.view_count already exists, skip");
            } else {
                log.warn("Database migration knowledge_doc.view_count failed: {}", msg);
            }
        }
    }

    /**
     * 清洗历史数据中的页面元数据（浏览次数、发布时间、来源等模板内容）
     * 只处理 content 仍含这些关键词的旧数据，避免重复处理
     */
    private void cleanMetadataFromExistingContent() {
        try {
            // 查找还有元数据残留的记录（用"浏览次数"作为探针）
            List<Long> dirtyIds = jdbcTemplate.queryForList(
                    "SELECT id FROM knowledge_doc WHERE content LIKE '%浏览次数%' OR content LIKE '%发布时间%' OR content LIKE '%来源：%'",
                    Long.class);
            if (dirtyIds.isEmpty()) {
                log.debug("Database migration: no metadata to clean in knowledge_doc");
                return;
            }

            int cleaned = 0;
            for (Long id : dirtyIds) {
                try {
                    String content = jdbcTemplate.queryForObject(
                            "SELECT content FROM knowledge_doc WHERE id = ?", String.class, id);
                    if (content != null) {
                        String cleanedContent = METADATA_PATTERN.matcher(content)
                                .replaceAll("").replaceAll("\\s{2,}", " ").trim();
                        if (!cleanedContent.equals(content)) {
                            jdbcTemplate.update("UPDATE knowledge_doc SET content = ? WHERE id = ?",
                                    cleanedContent, id);
                            cleaned++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to clean metadata for knowledge_doc id={}: {}", id, e.getMessage());
                }
            }
            if (cleaned > 0) {
                log.info("Database migration: cleaned metadata from {} knowledge_doc records", cleaned);
            }
        } catch (Exception e) {
            log.warn("Database migration cleanMetadata failed: {}", e.getMessage());
        }
    }
}
