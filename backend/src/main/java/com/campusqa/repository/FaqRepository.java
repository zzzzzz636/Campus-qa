package com.campusqa.repository;

import java.util.List;
import java.util.Optional;

import com.campusqa.model.Faq;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class FaqRepository {

    private static final RowMapper<Faq> FAQ_ROW_MAPPER = (rs, rowNum) -> new Faq(
            rs.getLong("id"),
            rs.getString("question"),
            rs.getString("answer"),
            rs.getString("category"),
            rs.getString("source"),
            rs.getInt("view_count"),
            rs.getString("created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public FaqRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Faq> findAll() {
        return findAll(null, null);
    }

    public List<Faq> findAll(String keyword, String category) {
        StringBuilder sql = new StringBuilder(
                "SELECT f.id, f.question, f.answer, COALESCE(c.name, '未分类') AS category, " +
                        "f.source, f.view_count, f.created_at " +
                        "FROM faq f LEFT JOIN category c ON f.category_id = c.id " +
                        "WHERE f.enabled = 1"
        );
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();

        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (f.question LIKE ? OR f.answer LIKE ? OR c.name LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        if (StringUtils.hasText(category)) {
            sql.append(" AND c.name = ?");
            params.add(category.trim());
        }

        sql.append(" ORDER BY f.id DESC");
        return jdbcTemplate.query(
                sql.toString(),
                FAQ_ROW_MAPPER,
                params.toArray()
        );
    }

    public Optional<Faq> findById(long id) {
        List<Faq> results = jdbcTemplate.query(
                "SELECT f.id, f.question, f.answer, COALESCE(c.name, '未分类') AS category, " +
                        "f.source, f.view_count, f.created_at " +
                        "FROM faq f LEFT JOIN category c ON f.category_id = c.id " +
                        "WHERE f.id = ? AND f.enabled = 1",
                FAQ_ROW_MAPPER,
                id
        );
        return results.stream().findFirst();
    }

    public List<Faq> search(String keyword) {
        String pattern = "%" + keyword + "%";
        return jdbcTemplate.query(
                "SELECT f.id, f.question, f.answer, COALESCE(c.name, '未分类') AS category, " +
                        "f.source, f.view_count, f.created_at " +
                        "FROM faq f LEFT JOIN category c ON f.category_id = c.id " +
                "WHERE f.enabled = 1 AND (f.question LIKE ? OR f.answer LIKE ? OR c.name LIKE ? OR f.source LIKE ?) " +
                "ORDER BY f.view_count DESC, f.id DESC",
                FAQ_ROW_MAPPER,
                pattern,
                pattern,
                pattern,
                pattern
        );
    }

    public List<Faq> findHot(int limit) {
        return jdbcTemplate.query(
                "SELECT f.id, f.question, f.answer, COALESCE(c.name, '未分类') AS category, " +
                        "f.source, f.view_count, f.created_at " +
                        "FROM faq f LEFT JOIN category c ON f.category_id = c.id " +
                        "WHERE f.enabled = 1 " +
                        "ORDER BY f.view_count DESC, f.id DESC LIMIT ?",
                FAQ_ROW_MAPPER,
                limit
        );
    }

    public int countEnabled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM faq WHERE enabled = 1",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    public int countHot() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM faq WHERE enabled = 1 AND view_count > 0",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    public void incrementViewCount(long id) {
        jdbcTemplate.update("UPDATE faq SET view_count = view_count + 1 WHERE id = ?", id);
    }

    public boolean existsByQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM faq WHERE enabled = 1 AND question = ?",
                Integer.class,
                question.trim()
        );
        return count != null && count > 0;
    }

    public Long save(String question, String answer, String category, String source) {
        Long categoryId = findOrCreateCategoryId(category);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator statementCreator = connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO faq (question, answer, category_id, source, view_count) VALUES (?, ?, ?, ?, 0)",
                    new String[]{"id"}
            );
            ps.setString(1, question);
            ps.setString(2, answer);
            ps.setLong(3, categoryId);
            ps.setString(4, source);
            return ps;
        };

        jdbcTemplate.update(statementCreator, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public int update(long id, String question, String answer, String category, String source) {
        Long categoryId = findOrCreateCategoryId(category);
        return jdbcTemplate.update(
                "UPDATE faq SET question = ?, answer = ?, category_id = ?, source = ?, " +
                        "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND enabled = 1",
                question,
                answer,
                categoryId,
                source,
                id
        );
    }

    public int deleteById(long id) {
        return jdbcTemplate.update(
                "UPDATE faq SET enabled = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND enabled = 1",
                id
        );
    }

    private Long findOrCreateCategoryId(String category) {
        String name = StringUtils.hasText(category) ? category.trim() : "未分类";
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM category WHERE name = ?",
                (rs, rowNum) -> rs.getLong("id"),
                name
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO category (name, description) VALUES (?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, name);
            ps.setString(2, "管理员新增分类");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("分类创建失败");
        }
        return key.longValue();
    }
}
