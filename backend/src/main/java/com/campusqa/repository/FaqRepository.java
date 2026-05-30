package com.campusqa.repository;

import java.util.List;
import java.util.Optional;

import com.campusqa.model.Faq;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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
        return jdbcTemplate.query(
                "SELECT id, question, answer, category, source, view_count, created_at " +
                        "FROM faq ORDER BY id DESC",
                FAQ_ROW_MAPPER
        );
    }

    public Optional<Faq> findById(long id) {
        List<Faq> results = jdbcTemplate.query(
                "SELECT id, question, answer, category, source, view_count, created_at " +
                        "FROM faq WHERE id = ?",
                FAQ_ROW_MAPPER,
                id
        );
        return results.stream().findFirst();
    }

    public List<Faq> search(String keyword) {
        String pattern = "%" + keyword + "%";
        return jdbcTemplate.query(
                "SELECT id, question, answer, category, source, view_count, created_at " +
                        "FROM faq WHERE question LIKE ? OR answer LIKE ? OR category LIKE ? " +
                        "ORDER BY view_count DESC, id DESC",
                FAQ_ROW_MAPPER,
                pattern,
                pattern,
                pattern
        );
    }

    public void incrementViewCount(long id) {
        jdbcTemplate.update("UPDATE faq SET view_count = view_count + 1 WHERE id = ?", id);
    }
}

