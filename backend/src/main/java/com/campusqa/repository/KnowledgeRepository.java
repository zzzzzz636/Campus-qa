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
            rs.getInt("view_count"),
            rs.getString("created_at"),
            rs.getString("updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KnowledgeDoc> findAll(String keyword, String category) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, content, COALESCE(category, '未分类') AS category, " +
                        "source_url, source_type, COALESCE(view_count, 0) AS view_count, " +
                        "created_at, COALESCE(updated_at, created_at) AS updated_at " +
                        "FROM knowledge_doc WHERE 1 = 1"
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
                        "source_url, source_type, COALESCE(view_count, 0) AS view_count, " +
                        "created_at, COALESCE(updated_at, created_at) AS updated_at " +
                        "FROM knowledge_doc WHERE id = ?",
                KNOWLEDGE_ROW_MAPPER,
                id
        );
        return results.stream().findFirst();
    }

    public Long save(String title, String content, String category, String sourceUrl, String sourceType) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator statementCreator = connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO knowledge_doc (title, content, category, source_url, source_type, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
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

    public int incrementViewCount(long id) {
        return jdbcTemplate.update(
                "UPDATE knowledge_doc SET view_count = COALESCE(view_count, 0) + 1 WHERE id = ?", id);
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
                "UPDATE knowledge_doc SET title = ?, content = ?, category = ?, source_url = ?, source_type = ?, " +
                        "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
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

    /**
     * 清理低质量资料：正文过短或为空的记录
     * @return 删除条数
     */
    /**
     * 重置知识资料库：清关联日志 → 清资料表 → 重置自增ID
     * @return 删除条数
     */
    public int resetAll() {
        // 只清理爬虫采集的数据，保留人工录入/导入的资料
        jdbcTemplate.update(
                "DELETE FROM query_log WHERE matched_knowledge_id IN " +
                "(SELECT id FROM knowledge_doc WHERE source_type IN ('华工官网','官网爬虫','OFFICIAL','CRAWLER'))");
        int deleted = jdbcTemplate.update(
                "DELETE FROM knowledge_doc WHERE source_type IN ('华工官网','官网爬虫','OFFICIAL','CRAWLER')");
        // 重置自增序列（只当全部清空时才重置）
        Integer remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_doc", Integer.class);
        if (remaining != null && remaining == 0) {
            jdbcTemplate.update("DELETE FROM sqlite_sequence WHERE name='knowledge_doc'");
        }
        return deleted;
    }

    public int cleanupLowQuality(int minContentLength) {
        return jdbcTemplate.update(
                "DELETE FROM knowledge_doc WHERE LENGTH(COALESCE(content, '')) < ?",
                minContentLength
        );
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_doc", Integer.class);
        return count == null ? 0 : count;
    }
}
