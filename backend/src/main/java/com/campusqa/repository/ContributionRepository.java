package com.campusqa.repository;

import java.util.List;
import java.util.Optional;

import com.campusqa.model.Contribution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ContributionRepository {

    private static final RowMapper<Contribution> CONTRIBUTION_ROW_MAPPER = (rs, rowNum) -> new Contribution(
            rs.getLong("id"),
            rs.getString("question"),
            rs.getString("suggested_answer"),
            rs.getString("category"),
            rs.getString("status"),
            rs.getString("created_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public ContributionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Contribution> findByStatus(String status) {
        return jdbcTemplate.query(
                "SELECT ct.id, ct.question, ct.suggested_answer, COALESCE(c.name, '未分类') AS category, " +
                        "ct.status, ct.created_at " +
                        "FROM contribution ct LEFT JOIN category c ON ct.category_id = c.id " +
                        "WHERE ct.status = ? ORDER BY ct.id DESC",
                CONTRIBUTION_ROW_MAPPER,
                status
        );
    }

    public List<Contribution> findAll(String status) {
        if (status == null) {
            return jdbcTemplate.query(
                    "SELECT ct.id, ct.question, ct.suggested_answer, COALESCE(c.name, '未分类') AS category, " +
                            "ct.status, ct.created_at " +
                            "FROM contribution ct LEFT JOIN category c ON ct.category_id = c.id " +
                            "ORDER BY ct.created_at DESC, ct.id DESC",
                    CONTRIBUTION_ROW_MAPPER
            );
        }

        return jdbcTemplate.query(
                "SELECT ct.id, ct.question, ct.suggested_answer, COALESCE(c.name, '未分类') AS category, " +
                        "ct.status, ct.created_at " +
                        "FROM contribution ct LEFT JOIN category c ON ct.category_id = c.id " +
                        "WHERE ct.status = ? ORDER BY ct.created_at DESC, ct.id DESC",
                CONTRIBUTION_ROW_MAPPER,
                status
        );
    }

    public Optional<Contribution> findById(long id) {
        List<Contribution> results = jdbcTemplate.query(
                "SELECT ct.id, ct.question, ct.suggested_answer, COALESCE(c.name, '未分类') AS category, " +
                        "ct.status, ct.created_at " +
                        "FROM contribution ct LEFT JOIN category c ON ct.category_id = c.id " +
                        "WHERE ct.id = ?",
                CONTRIBUTION_ROW_MAPPER,
                id
        );
        return results.stream().findFirst();
    }

    public Long save(String question, String suggestedAnswer, String category, String status) {
        Long categoryId = findCategoryIdByName(category);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator statementCreator = connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO contribution (question, suggested_answer, category_id, status) VALUES (?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, question);
            ps.setString(2, suggestedAnswer);
            if (categoryId == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setLong(3, categoryId);
            }
            ps.setString(4, status);
            return ps;
        };

        jdbcTemplate.update(statementCreator, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public int updateStatus(long id, String status) {
        return jdbcTemplate.update(
                "UPDATE contribution SET status = ?, reviewed_at = CURRENT_TIMESTAMP WHERE id = ?",
                status,
                id
        );
    }

    public int countByStatus(String status) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contribution WHERE status = ?",
                Integer.class,
                status
        );
        return count == null ? 0 : count;
    }

    private Long findCategoryIdByName(String category) {
        List<Long> results = jdbcTemplate.query(
                "SELECT id FROM category WHERE name = ?",
                (rs, rowNum) -> rs.getLong("id"),
                category
        );
        return results.stream().findFirst().orElse(null);
    }
}
