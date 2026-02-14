package com.example.vaultrotate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

/**
 * Reads dynamic DB credentials from Vault using the Database secrets engine.
 */
@Service
public class VaultDbCredentialsService {

    private final VaultTemplate vaultTemplate;

    @Value("${vault.db.role:demo-role}")
    private String defaultRole;

    public VaultDbCredentialsService(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    public DbCredentials fetch(String role) {
        String effective = role == null || role.isBlank() ? defaultRole : role;
        String path = "database/creds/" + effective;
        VaultResponse resp = vaultTemplate.read(path);
        if (resp == null || resp.getData() == null) {
            throw new IllegalStateException("no credentials found at: " + path);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        String username = (String) data.get("username");
        String password = (String) data.get("password");
        String lease = resp.getLeaseId();
        return new DbCredentials(username, password, lease);
    }

    public static record DbCredentials(String username, String password, String leaseId) {}
}
