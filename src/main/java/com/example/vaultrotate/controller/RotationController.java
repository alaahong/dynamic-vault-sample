package com.example.vaultrotate.controller;

import com.example.vaultrotate.repository.SampleRepository;
import com.example.vaultrotate.service.RotationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RotationController {

    private final RotationService rotationService;
    private final SampleRepository repo;

    public RotationController(RotationService rotationService, SampleRepository repo) {
        this.rotationService = rotationService;
        this.repo = repo;
    }

    @PostMapping("/api/rotate")
    public ResponseEntity<String> rotate(@RequestParam(required = false) String role) {
        boolean ok = rotationService.rotate(role);
        return ok ? ResponseEntity.ok("rotation succeeded") : ResponseEntity.status(500).body("rotation failed — kept previous credentials");
    }

    @GetMapping("/api/db-user")
    public ResponseEntity<String> currentDbUser() {
        String user = repo.currentDbUser();
        return ResponseEntity.ok(user);
    }
}
