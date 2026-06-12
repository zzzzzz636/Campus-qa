package com.campusqa.repository;

import java.util.List;

import com.campusqa.model.ImportHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ImportHistoryRepository {

    private static final RowMapper<ImportHistory> IMPORT_HISTORY_ROW_MAPPER = (rs, rowNum) -> new ImportHistory(
            rs.getLong("id"),
            rs.getString("file_name"),
            rs.getString("import_type"),
            rs.getInt("success_count"),
            rs.getInt("fail_count"),
            rs.getString("message"),
            rs.getString("created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public ImportHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(String fileName, String importType, int successCount, int failCount, String message) {
        return jdbcTemplate.update(
                "INSERT INTO import_history (file_name, import_type, success_count, fail_count, message) " +
                        "VALUES (?, ?, ?, ?, ?)",
                fileName,
                importType,
                successCount,
                failCount,
                message
        );
    }

    public List<ImportHistory> findRecent() {
        return jdbcTemplate.query(
                "SELECT id, file_name, import_type, success_count, fail_count, message, created_at " +
                        "FROM import_history ORDER BY created_at DESC, id DESC",
                IMPORT_HISTORY_ROW_MAPPER
        );
    }
}
