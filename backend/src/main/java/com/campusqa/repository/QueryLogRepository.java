package com.campusqa.repository;

import com.campusqa.model.QueryLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class QueryLogRepository {

    private static final RowMapper<QueryLog> QUERY_LOG_ROW_MAPPER = (rs, rowNum) -> {
        Long matchedFaqId = rs.getLong("matched_faq_id");
        if (rs.wasNull()) {
            matchedFaqId = null;
        }
        return new QueryLog(
                rs.getLong("id"),
                rs.getString("query_text"),
                matchedFaqId,
                rs.getString("created_at")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public QueryLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(String queryText, Long matchedFaqId) {
        return jdbcTemplate.update(
                "INSERT INTO query_log (query_text, matched_faq_id) VALUES (?, ?)",
                queryText,
                matchedFaqId
        );
    }

    public java.util.List<QueryLog> findRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT id, query_text, matched_faq_id, created_at FROM query_log ORDER BY id DESC LIMIT ?",
                QUERY_LOG_ROW_MAPPER,
                limit
        );
    }
}
