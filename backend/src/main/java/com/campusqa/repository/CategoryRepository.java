package com.campusqa.repository;

import java.util.List;

import com.campusqa.model.Category;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public CategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Category> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, description FROM category ORDER BY id",
                (rs, rowNum) -> new Category(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description")
                )
        );
    }

    public List<Category> findUsedByFaq() {
        return jdbcTemplate.query(
                "SELECT DISTINCT c.id, c.name, c.description " +
                        "FROM category c INNER JOIN faq f ON f.category_id = c.id " +
                        "WHERE f.enabled = 1 ORDER BY c.name",
                (rs, rowNum) -> new Category(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description")
                )
        );
    }
}
