package com.campusqa.repository;

import java.util.List;
import java.util.Optional;

import com.campusqa.model.Admin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Admin> findByUsername(String username) {
        List<Admin> results = jdbcTemplate.query(
                "SELECT id, username, password_hash FROM admin WHERE username = ?",
                (rs, rowNum) -> new Admin(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash")
                ),
                username
        );
        return results.stream().findFirst();
    }

    public int updateLastLoginAt(long id) {
        return jdbcTemplate.update(
                "UPDATE admin SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?",
                id
        );
    }
}
