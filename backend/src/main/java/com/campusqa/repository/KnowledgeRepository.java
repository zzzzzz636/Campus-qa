package com.campusqa.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.campusqa.model.KnowledgeDoc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class KnowledgeRepository {

    private static final RowMapper<KnowledgeDoc> KNOWLEDGE_ROW_MAPPER = (rs, rowNum) -> new KnowledgeDoc(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("category"),
            rs.getString("source_url"),
            rs.getString("source_type"),
            rs.getString("created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KnowledgeDoc> findAll(String keyword, String category) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, content, COALESCE(category, '未分类') AS category, " +
                        "source_url, source_type, created_at FROM knowledge_doc WHERE 1 = 1"
        );
        ArrayList<Object> params = new ArrayList<>();

        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (title LIKE ? OR content LIKE ? OR category LIKE ? OR source_url LIKE ? OR source_type LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        if (StringUtils.hasText(category)) {
            sql.append(" AND category = ?");
            params.add(category.trim());
        }

        sql.append(" ORDER BY created_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), KNOWLEDGE_ROW_MAPPER, params.toArray());
    }

    public Optional<KnowledgeDoc> findById(long id) {
        List<KnowledgeDoc> results = jdbcTemplate.query(
                "SELECT id, title, content, COALESCE(category, '未分类') AS category, " +
                        "source_url, source_type, created_at FROM knowledge_doc WHERE id = ?",
                KNOWLEDGE_ROW_MAPPER,
                id
        );
        return results.stream().findFirst();
    }

    public Optional<KnowledgeDoc> findBySourceUrl(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return Optional.empty();
        }
        List<KnowledgeDoc> results = jdbcTemplate.query(
                "SELECT id, title, content, COALESCE(category, '未分类') AS category, " +
                        "source_url, source_type, created_at FROM knowledge_doc WHERE source_url = ?",
                KNOWLEDGE_ROW_MAPPER,
                sourceUrl.trim()
        );
        return results.stream().findFirst();
    }

    public Long save(String title, String content, String category, String sourceUrl, String sourceType) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator statementCreator = connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO knowledge_doc (title, content, category, source_url, source_type) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setString(3, category);
            ps.setString(4, sourceUrl);
            ps.setString(5, sourceType);
            return ps;
        };

        jdbcTemplate.update(statementCreator, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public boolean existsBySourceUrl(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_doc WHERE source_url = ?",
                Integer.class,
                sourceUrl.trim()
        );
        return count != null && count > 0;
    }

    public int update(long id, String title, String content, String category, String sourceUrl, String sourceType) {
        return jdbcTemplate.update(
                "UPDATE knowledge_doc SET title = ?, content = ?, category = ?, source_url = ?, source_type = ? " +
                        "WHERE id = ?",
                title,
                content,
                category,
                sourceUrl,
                sourceType,
                id
        );
    }

    public int deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM knowledge_doc WHERE id = ?", id);
    }
}
