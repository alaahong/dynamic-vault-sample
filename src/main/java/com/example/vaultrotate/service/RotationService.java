package com.example.vaultrotate.service;

import org.springframework.stereotype.Service;

@Service
public class RotationService {

    private final DataSourceManager manager;

    public RotationService(DataSourceManager manager) {
        this.manager = manager;
    }

    public boolean rotate(String role) {
        return manager.rotate(role);
    }
}
