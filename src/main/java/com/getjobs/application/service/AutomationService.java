package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.dto.AutomationStartRequest;
import com.getjobs.application.dto.AutomationTaskRequest;
import com.getjobs.application.entity.AutomationAuditEntity;
import com.getjobs.application.entity.AutomationTaskEntity;
import com.getjobs.application.mapper.AutomationAuditMapper;
import com.getjobs.application.mapper.AutomationTaskMapper;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.DeliveryExecutionOptions;
import com.getjobs.worker.service.JobPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationService {
    public static final String MODE_DRY_RUN = "DRY_RUN";
    public static final String MODE_REVIEW_THEN_APPLY = "REVIEW_THEN_APPLY";
    public static final String MODE_AUTO_APPLY = "AUTO_APPLY";
    public static final String AUTO_APPLY_CONFIRMATION_PHRASE = "AUTO_APPLY_ONE_JOB";

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_RATE_LIMITED = "RATE_LIMITED";
    public static final String STATUS_USER_STOPPED = "USER_STOPPED";

    private static final List<String> SUPPORTED_PLATFORMS = List.of("boss", "liepin", "51job", "zhilian");

    private final AutomationTaskMapper automationTaskMapper;
    private final AutomationAuditMapper automationAuditMapper;
    private final List<JobPlatformService> platformServices;
    private final PlaywrightManager playwrightManager;
    private final Set<Long> stopRequestedTaskIds = ConcurrentHashMap.newKeySet();

    public AutomationTaskEntity createTask(AutomationTaskRequest request) {
        AutomationTaskRequest safeRequest = request == null ? new AutomationTaskRequest() : request;
        AutomationTaskEntity entity = new AutomationTaskEntity();
        entity.setName(nonBlank(safeRequest.getName(), "自动化投递任务"));
        entity.setPlatforms(String.join(",", normalizePlatforms(safeRequest.getPlatforms())));
        entity.setMode(normalizeMode(safeRequest.getMode()));
        entity.setStatus(STATUS_QUEUED);
        entity.setFilterTemplateId(safeRequest.getFilterTemplateId());
        entity.setKeywords(trimToNull(safeRequest.getKeywords()));
        entity.setCity(trimToNull(safeRequest.getCity()));
        entity.setMaxApplications(clamp(safeRequest.getMaxApplications(), 1, 1, 1));
        entity.setMaxDailyApplications(clamp(safeRequest.getMaxDailyApplications(), 1, 1, 1));
        entity.setAllowRealActions(Boolean.TRUE.equals(safeRequest.getAllowRealActions()) ? 1 : 0);
        entity.setReviewApproved(0);
        entity.setLastMessage("Task queued. No platform action has been started.");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.insert(entity);
        audit(entity.getId(), "-", "TASK_CREATED", "QUEUED", entity.getLastMessage());
        return entity;
    }

    public List<AutomationTaskEntity> listTasks() {
        return automationTaskMapper.selectList(new QueryWrapper<AutomationTaskEntity>()
                .orderByDesc("id")
                .last("LIMIT 100"));
    }

    public AutomationTaskEntity getTask(Long id) {
        AutomationTaskEntity task = automationTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + id);
        }
        return task;
    }

    public List<AutomationAuditEntity> listAudit(Long taskId, int limit) {
        QueryWrapper<AutomationAuditEntity> wrapper = new QueryWrapper<AutomationAuditEntity>()
                .orderByDesc("id")
                .last("LIMIT " + clamp(limit, 1, 500, 100));
        if (taskId != null) {
            wrapper.eq("task_id", taskId);
        }
        return automationAuditMapper.selectList(wrapper);
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("supportedPlatforms", SUPPORTED_PLATFORMS);
        status.put("defaultMode", MODE_DRY_RUN);
        status.put("realActionsRequireExplicitFlag", true);
        status.put("autoApplyConfirmationPhrase", AUTO_APPLY_CONFIRMATION_PHRASE);
        status.put("maxApplicationsPerTask", 1);
        status.put("maxDailyApplicationsPerPlatform", 1);
        status.put("captchaBypassSupported", false);
        status.put("stealthBypassSupported", false);
        status.put("runningTasks", automationTaskMapper.selectCount(new QueryWrapper<AutomationTaskEntity>()
                .eq("status", STATUS_RUNNING)));
        return status;
    }

    public AutomationTaskEntity startTask(Long id) {
        return startTask(id, null);
    }

    public AutomationTaskEntity startTask(Long id, AutomationStartRequest request) {
        AutomationTaskEntity task = getTask(id);
        if (STATUS_RUNNING.equals(task.getStatus())) {
            return task;
        }
        stopRequestedTaskIds.remove(id);
        if (MODE_DRY_RUN.equals(task.getMode())) {
            return completeDryRun(task);
        }
        if (MODE_REVIEW_THEN_APPLY.equals(task.getMode()) && !Integer.valueOf(1).equals(task.getReviewApproved())) {
            return pauseForReview(task);
        }
        if (task.getAllowRealActions() == null || task.getAllowRealActions() == 0) {
            return block(task, "Real platform actions require allowRealActions=true.");
        }
        if (!hasValidConfirmationPhrase(request)) {
            return block(task, "Real platform actions require confirmation phrase: " + AUTO_APPLY_CONFIRMATION_PHRASE);
        }
        List<String> platforms = parsePlatforms(task.getPlatforms());
        for (String platform : platforms) {
            if (!playwrightManager.isLoggedIn(platform)) {
                return block(task, "Platform is not logged in: " + platform);
            }
            long startedToday = countRealStartsToday(platform);
            if (startedToday + safeMaxApplications() > safeDailyLimit()) {
                task.setStatus(STATUS_RATE_LIMITED);
                task.setLastMessage("Daily application limit reached for " + platform);
                task.setUpdatedAt(LocalDateTime.now());
                automationTaskMapper.updateById(task);
                audit(task.getId(), platform, "RATE_LIMITED", "BLOCKED", task.getLastMessage());
                return task;
            }
        }
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setCompletedAt(null);
        task.setLastMessage("Real platform execution started under task-level limits.");
        task.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "TASK_STARTED", "RUNNING", task.getLastMessage());
        CompletableFuture.runAsync(() -> runRealTask(task.getId()));
        return task;
    }

    public AutomationTaskEntity approveTask(Long id) {
        AutomationTaskEntity task = getTask(id);
        task.setReviewApproved(1);
        task.setStatus(STATUS_QUEUED);
        task.setLastMessage("Review approved. Task can now be started.");
        task.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "REVIEW_APPROVED", "QUEUED", task.getLastMessage());
        return task;
    }

    public AutomationTaskEntity pauseTask(Long id) {
        AutomationTaskEntity task = getTask(id);
        stopRequestedTaskIds.add(id);
        stopPlatformServices(task);
        task.setStatus(STATUS_PAUSED);
        task.setLastMessage("Pause requested. Running platform service was asked to stop cooperatively.");
        task.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "TASK_PAUSED", "PAUSED", task.getLastMessage());
        return task;
    }

    public AutomationTaskEntity resumeTask(Long id) {
        AutomationTaskEntity task = getTask(id);
        if (!STATUS_PAUSED.equals(task.getStatus())) {
            return task;
        }
        task.setStatus(STATUS_QUEUED);
        task.setLastMessage("Task resumed to queue.");
        task.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "TASK_RESUMED", "QUEUED", task.getLastMessage());
        return task;
    }

    public AutomationTaskEntity stopTask(Long id) {
        AutomationTaskEntity task = getTask(id);
        stopRequestedTaskIds.add(id);
        stopPlatformServices(task);
        task.setStatus(STATUS_USER_STOPPED);
        task.setCompletedAt(LocalDateTime.now());
        task.setLastMessage("Task stopped by user.");
        task.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "TASK_STOPPED", "USER_STOPPED", task.getLastMessage());
        return task;
    }

    private AutomationTaskEntity completeDryRun(AutomationTaskEntity task) {
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setLastMessage("Dry-run started. No platform page was opened and no delivery action was triggered.");
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "TASK_STARTED", "DRY_RUN", task.getLastMessage());
        for (String platform : parsePlatforms(task.getPlatforms())) {
            audit(task.getId(), platform, "DISCOVER", "DRY_RUN_SKIPPED", "Candidate discovery adapter is not allowed to click in dry-run.");
            audit(task.getId(), platform, "SCORE", "DRY_RUN_READY", "Use keywords/city/template to review candidates before real actions.");
        }
        task.setStatus(STATUS_COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setLastMessage("Dry-run completed without real platform actions.");
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "TASK_COMPLETED", "COMPLETED", task.getLastMessage());
        return task;
    }

    private AutomationTaskEntity pauseForReview(AutomationTaskEntity task) {
        task.setStatus(STATUS_PAUSED);
        task.setLastMessage("Review confirmation is required before any real platform action.");
        task.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "REVIEW_REQUIRED", "PAUSED", task.getLastMessage());
        return task;
    }

    private AutomationTaskEntity block(AutomationTaskEntity task, String message) {
        task.setStatus(STATUS_BLOCKED);
        task.setLastMessage(message);
        task.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.updateById(task);
        audit(task.getId(), "-", "TASK_BLOCKED", "BLOCKED", message);
        return task;
    }

    private void runRealTask(Long taskId) {
        AutomationTaskEntity task = getTask(taskId);
        Map<String, JobPlatformService> services = platformServiceMap();
        DeliveryExecutionOptions executionOptions = DeliveryExecutionOptions.realApply(safeMaxApplications());
        try {
            for (String platform : parsePlatforms(task.getPlatforms())) {
                if (stopRequestedTaskIds.contains(taskId)) {
                    completeStopped(taskId);
                    return;
                }
                JobPlatformService service = services.get(platform);
                if (service == null) {
                    audit(taskId, platform, "PLATFORM_UNSUPPORTED", "FAILED", "No service found for platform.");
                    continue;
                }
                audit(taskId, platform, "REAL_DELIVERY_STARTED", "RUNNING", "Delegated to existing platform delivery service.");
                service.executeDelivery(message -> recordProgress(taskId, message), executionOptions);
            }
            AutomationTaskEntity latest = getTask(taskId);
            if (!stopRequestedTaskIds.contains(taskId) && STATUS_RUNNING.equals(latest.getStatus())) {
                latest.setStatus(STATUS_COMPLETED);
                latest.setCompletedAt(LocalDateTime.now());
                latest.setLastMessage("Task completed. Check audit entries for platform results.");
                latest.setUpdatedAt(LocalDateTime.now());
                automationTaskMapper.updateById(latest);
                audit(taskId, "-", "TASK_COMPLETED", "COMPLETED", latest.getLastMessage());
            }
        } catch (Exception e) {
            log.error("Automation task failed id={}", taskId, e);
            AutomationTaskEntity latest = getTask(taskId);
            latest.setStatus(STATUS_FAILED);
            latest.setCompletedAt(LocalDateTime.now());
            latest.setLastMessage("Task failed: " + e.getMessage());
            latest.setUpdatedAt(LocalDateTime.now());
            automationTaskMapper.updateById(latest);
            audit(taskId, "-", "TASK_FAILED", "FAILED", latest.getLastMessage());
        } finally {
            stopRequestedTaskIds.remove(taskId);
        }
    }

    private void recordProgress(Long taskId, JobProgressMessage message) {
        String platform = message == null ? "-" : nonBlank(message.getPlatform(), "-");
        String type = message == null ? "info" : nonBlank(message.getType(), "info");
        String text = message == null ? "" : nonBlank(message.getMessage(), "");
        audit(taskId, platform, "PLATFORM_PROGRESS", type.toUpperCase(Locale.ROOT), text);
        if (containsRiskText(text)) {
            AutomationTaskEntity task = getTask(taskId);
            task.setStatus("BLOCKED_BY_CAPTCHA");
            task.setLastMessage("Platform risk/captcha message observed: " + text);
            task.setUpdatedAt(LocalDateTime.now());
            automationTaskMapper.updateById(task);
            stopRequestedTaskIds.add(taskId);
            stopPlatformServices(task);
        }
    }

    private boolean containsRiskText(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return List.of(
                        "验证码",
                        "安全验证",
                        "风控",
                        "频控",
                        "操作过于频繁",
                        "访问受限",
                        "账号异常",
                        "captcha",
                        "verification",
                        "too frequent",
                        "access denied",
                        "account restricted"
                )
                .stream()
                .anyMatch(normalized::contains);
    }

    private void completeStopped(Long taskId) {
        AutomationTaskEntity task = getTask(taskId);
        task.setStatus(STATUS_USER_STOPPED);
        task.setCompletedAt(LocalDateTime.now());
        task.setLastMessage("Task stopped before all platforms completed.");
        task.setUpdatedAt(LocalDateTime.now());
        automationTaskMapper.updateById(task);
        audit(taskId, "-", "TASK_STOPPED", "USER_STOPPED", task.getLastMessage());
    }

    private void stopPlatformServices(AutomationTaskEntity task) {
        Map<String, JobPlatformService> services = platformServiceMap();
        for (String platform : parsePlatforms(task.getPlatforms())) {
            JobPlatformService service = services.get(platform);
            if (service != null && service.isRunning()) {
                service.stopDelivery();
            }
        }
    }

    private Map<String, JobPlatformService> platformServiceMap() {
        return platformServices.stream()
                .collect(Collectors.toMap(JobPlatformService::getPlatformName, Function.identity(), (a, b) -> a));
    }

    private void audit(Long taskId, String platform, String eventType, String result, String message) {
        AutomationAuditEntity audit = new AutomationAuditEntity();
        audit.setTaskId(taskId);
        audit.setPlatform(nonBlank(platform, "-"));
        audit.setEventType(nonBlank(eventType, "-"));
        audit.setResult(nonBlank(result, "-"));
        audit.setMessage(nonBlank(message, "-"));
        audit.setCreatedAt(LocalDateTime.now());
        automationAuditMapper.insert(audit);
    }

    private long countRealStartsToday(String platform) {
        Long count = automationAuditMapper.selectCount(new QueryWrapper<AutomationAuditEntity>()
                .eq("platform", platform)
                .eq("event_type", "REAL_DELIVERY_STARTED")
                .ge("created_at", LocalDate.now().atStartOfDay()));
        return count == null ? 0 : count;
    }

    private List<String> normalizePlatforms(List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return List.of("boss");
        }
        List<String> normalized = platforms.stream()
                .map(this::normalizePlatform)
                .filter(SUPPORTED_PLATFORMS::contains)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("boss") : normalized;
    }

    private String normalizePlatform(String platform) {
        if (platform == null) {
            return "";
        }
        String value = platform.trim().toLowerCase(Locale.ROOT);
        if ("job51".equals(value)) {
            return "51job";
        }
        return value;
    }

    private List<String> parsePlatforms(String platforms) {
        if (platforms == null || platforms.isBlank()) {
            return List.of("boss");
        }
        return normalizePlatforms(Arrays.asList(platforms.split(",")));
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_DRY_RUN;
        }
        String value = mode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (MODE_REVIEW_THEN_APPLY.equals(value) || MODE_AUTO_APPLY.equals(value) || MODE_DRY_RUN.equals(value)) {
            return value;
        }
        return MODE_DRY_RUN;
    }

    private int safeMaxApplications() {
        return 1;
    }

    private int safeDailyLimit() {
        return 1;
    }

    private boolean hasValidConfirmationPhrase(AutomationStartRequest request) {
        if (request == null || request.getConfirmationPhrase() == null) {
            return false;
        }
        return AUTO_APPLY_CONFIRMATION_PHRASE.equals(request.getConfirmationPhrase().trim());
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int selected = value == null ? fallback : value;
        return Math.max(min, Math.min(max, selected));
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
