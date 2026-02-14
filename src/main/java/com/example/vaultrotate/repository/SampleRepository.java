package com.example.vaultrotate.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SampleRepository {

    private final JdbcTemplate jdbcTemplate;

    public SampleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String currentDbUser() {
        return jdbcTemplate.queryForObject("SELECT current_user", String.class);
    }
}
