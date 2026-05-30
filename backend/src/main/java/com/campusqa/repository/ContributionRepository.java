package com.campusqa.repository;

import java.util.List;

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
                "SELECT id, question, suggested_answer, category, status, created_at " +
                        "FROM contribution WHERE status = ? ORDER BY id DESC",
                CONTRIBUTION_ROW_MAPPER,
                status
        );
    }

    public Long save(String question, String suggestedAnswer, String category, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator statementCreator = connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO contribution (question, suggested_answer, category, status) VALUES (?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, question);
            ps.setString(2, suggestedAnswer);
            ps.setString(3, category);
            ps.setString(4, status);
            return ps;
        };

        jdbcTemplate.update(statementCreator, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }
}
