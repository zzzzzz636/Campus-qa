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
        Long matchedKnowledgeId = rs.getLong("matched_knowledge_id");
        if (rs.wasNull()) {
            matchedKnowledgeId = null;
        }
        return new QueryLog(
                rs.getLong("id"),
                rs.getString("query_text"),
                rs.getString("matched_type"),
                matchedFaqId,
                matchedKnowledgeId,
                rs.getInt("match_score"),
                rs.getString("created_at")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public QueryLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(String queryText, Long matchedFaqId) {
        String matchedType = matchedFaqId == null ? "NONE" : "FAQ";
        return save(queryText, matchedType, matchedFaqId, null, matchedFaqId == null ? 0 : 1);
    }

    public int save(String queryText, String matchedType, Long matchedFaqId, Long matchedKnowledgeId, Integer matchScore) {
        return jdbcTemplate.update(
                "INSERT INTO query_log (query_text, matched_type, matched_faq_id, matched_knowledge_id, match_score, found) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                queryText,
                normalizeMatchedType(matchedType),
                matchedFaqId,
                matchedKnowledgeId,
                matchScore == null ? 0 : matchScore,
                "NONE".equals(normalizeMatchedType(matchedType)) ? 0 : 1
        );
    }

    public java.util.List<QueryLog> findRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT id, query_text, COALESCE(matched_type, CASE WHEN matched_faq_id IS NOT NULL THEN 'FAQ' ELSE 'NONE' END) AS matched_type, " +
                        "matched_faq_id, matched_knowledge_id, COALESCE(match_score, 0) AS match_score, created_at FROM query_log " +
                        "ORDER BY created_at DESC, id DESC LIMIT ?",
                QUERY_LOG_ROW_MAPPER,
                limit
        );
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM query_log", Integer.class);
        return count == null ? 0 : count;
    }

    private String normalizeMatchedType(String matchedType) {
        if ("FAQ".equals(matchedType) || "KNOWLEDGE_DOC".equals(matchedType)) {
            return matchedType;
        }
        return "NONE";
    }
}
