package com.getjobs.application.controller;

import com.getjobs.application.dto.AutomationTaskRequest;
import com.getjobs.application.dto.AutomationStartRequest;
import com.getjobs.application.entity.AutomationAuditEntity;
import com.getjobs.application.entity.AutomationTaskEntity;
import com.getjobs.application.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
public class AutomationController {
    private final AutomationService automationService;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return automationService.status();
    }

    @GetMapping("/tasks")
    public List<AutomationTaskEntity> listTasks() {
        return automationService.listTasks();
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody(required = false) AutomationTaskRequest request) {
        return ok(automationService.createTask(request));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable Long id) {
        try {
            return ok(automationService.getTask(id));
        } catch (Exception e) {
            return bad(e);
        }
    }

    @PostMapping("/tasks/{id}/start")
    public ResponseEntity<Map<String, Object>> startTask(
            @PathVariable Long id,
            @RequestBody(required = false) AutomationStartRequest request
    ) {
        try {
            return ok(automationService.startTask(id, request));
        } catch (Exception e) {
            return bad(e);
        }
    }

    @PostMapping("/tasks/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveTask(@PathVariable Long id) {
        try {
            return ok(automationService.approveTask(id));
        } catch (Exception e) {
            return bad(e);
        }
    }

    @PostMapping("/tasks/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseTask(@PathVariable Long id) {
        try {
            return ok(automationService.pauseTask(id));
        } catch (Exception e) {
            return bad(e);
        }
    }

    @PostMapping("/tasks/{id}/resume")
    public ResponseEntity<Map<String, Object>> resumeTask(@PathVariable Long id) {
        try {
            return ok(automationService.resumeTask(id));
        } catch (Exception e) {
            return bad(e);
        }
    }

    @PostMapping("/tasks/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopTask(@PathVariable Long id) {
        try {
            return ok(automationService.stopTask(id));
        } catch (Exception e) {
            return bad(e);
        }
    }

    @GetMapping("/tasks/{id}/audit")
    public List<AutomationAuditEntity> listTaskAudit(
            @PathVariable Long id,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return automationService.listAudit(id, limit);
    }

    @GetMapping("/audit")
    public List<AutomationAuditEntity> listAudit(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return automationService.listAudit(null, limit);
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    private ResponseEntity<Map<String, Object>> bad(Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()
        ));
    }
}
