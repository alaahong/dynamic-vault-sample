package com.example.vaultrotate.service;

import com.example.vaultrotate.datasource.DelegatingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Manages current / previous DataSource and performs atomic swap on successful rotation.
 */
@Component
public class DataSourceManager {

    private final DelegatingDataSource delegatingDataSource;
    private final VaultDbCredentialsService vaultService;
    private final String jdbcUrl;

    // current and previous pools (HikariDataSource implements AutoCloseable)
    private volatile HikariDataSource current;
    private volatile HikariDataSource previous;

    public DataSourceManager(DelegatingDataSource delegatingDataSource,
                             VaultDbCredentialsService vaultService,
                             @Value("${vault.db.jdbc-url}") String jdbcUrl) {
        this.delegatingDataSource = delegatingDataSource;
        this.vaultService = vaultService;
        this.jdbcUrl = jdbcUrl;
    }

    @PostConstruct
    public void init() {
        // fetch credentials at startup and create initial pool
        var creds = vaultService.fetch(null);
        HikariDataSource ds = createPool(creds.username(), creds.password());
        this.current = ds;
        this.delegatingDataSource.setTargetDataSource(ds);
    }

    public synchronized boolean rotate(String role) {
        var creds = vaultService.fetch(role);
        HikariDataSource candidate = createPool(creds.username(), creds.password());

        // test connectivity
        try (Connection c = candidate.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1")) {
            ps.execute();
        } catch (Exception e) {
            // new pool cannot connect — discard and keep current
            try { candidate.close(); } catch (Exception ignore) {}
            return false;
        }

        // swap: candidate becomes current, keep previous for quick rollback
        HikariDataSource old = this.current;
        this.previous = old;
        this.current = candidate;
        this.delegatingDataSource.setTargetDataSource(candidate);

        // close old previous pool (if exists) to avoid unbounded resources
        if (previous != null && previous != old) {
            try { previous.close(); } catch (Exception ignore) {}
        }

        // close the old pool after a short grace period (optional) — immediate here
        if (old != null) {
            try { old.close(); } catch (Exception ignore) {}
        }

        return true;
    }

    private HikariDataSource createPool(String username, String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setPoolName("vault-ds-" + System.currentTimeMillis());
        cfg.setMaximumPoolSize(10);
        cfg.setConnectionTimeout(5000);
        return new HikariDataSource(cfg);
    }

    @PreDestroy
    public void shutdown() {
        if (current != null) try { current.close(); } catch (Exception ignore) {}
        if (previous != null) try { previous.close(); } catch (Exception ignore) {}
    }
}
