package com.example.vaultrotate.datasource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple delegating DataSource whose delegate can be swapped atomically at runtime.
 */
public class DelegatingDataSource extends AbstractDataSource {

    private final AtomicReference<DataSource> delegate = new AtomicReference<>();

    public DelegatingDataSource(DataSource initial) {
        this.delegate.set(initial);
    }

    public void setTargetDataSource(DataSource ds) {
        this.delegate.set(ds);
    }

    @Override
    public Connection getConnection() throws SQLException {
        DataSource ds = delegate.get();
        if (ds == null) {
            throw new SQLException("No target DataSource available");
        }
        return ds.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        DataSource ds = delegate.get();
        if (ds == null) {
            throw new SQLException("No target DataSource available");
        }
        return ds.getConnection(username, password);
    }
}
