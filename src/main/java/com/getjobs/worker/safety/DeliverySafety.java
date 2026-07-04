package com.getjobs.worker.safety;

import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 平台投递动作的统一安全门、计数器和审计日志工具。
 */
@Slf4j
public class DeliverySafety {
    private static final List<String> CAPTCHA_TEXTS = List.of(
            "验证码", "滑块", "安全验证", "访问验证", "请按住滑块", "captcha"
    );
    private static final List<String> RISK_TEXTS = List.of(
            "风控", "频控", "操作过于频繁", "访问受限", "异常访问", "请求过多",
            "今日投递太多", "达到上限", "次数过多", "稍后再试", "休息一下明天再来"
    );

    private final String platform;
    private final boolean dryRun;
    private final int maxDeliveries;
    private final boolean stopOnCaptcha;
    private final boolean stopOnRiskText;
    private final Consumer<String> progressEmitter;
    private int usedDeliverySlots;
    private boolean stopped;

    private DeliverySafety(
            String platform,
            boolean dryRun,
            int maxDeliveries,
            boolean stopOnCaptcha,
            boolean stopOnRiskText,
            Consumer<String> progressEmitter
    ) {
        this.platform = platform;
        this.dryRun = dryRun;
        this.maxDeliveries = Math.max(1, maxDeliveries);
        this.stopOnCaptcha = stopOnCaptcha;
        this.stopOnRiskText = stopOnRiskText;
        this.progressEmitter = progressEmitter;
    }

    /**
     * 根据平台配置创建安全门。
     */
    public static DeliverySafety create(
            String platform,
            Boolean dryRun,
            Integer maxDeliveries,
            Boolean stopOnCaptcha,
            Boolean stopOnRiskText,
            Consumer<String> progressEmitter
    ) {
        return new DeliverySafety(
                platform,
                dryRun == null || dryRun,
                maxDeliveries == null ? 1 : maxDeliveries,
                stopOnCaptcha == null || stopOnCaptcha,
                stopOnRiskText == null || stopOnRiskText,
                progressEmitter
        );
    }

    /**
     * 在真实动作前执行 dry-run、限量和风险文本检查。
     */
    public DeliveryDecision canProceed(Page page, DeliveryJobInfo jobInfo, String action) {
        DeliveryDecision riskDecision = checkRisk(page, jobInfo, action);
        if (!riskDecision.isAllowed()) {
            return riskDecision;
        }
        if (dryRun) {
            recordSkipped(jobInfo, action, DeliveryActionResult.DRY_RUN_SKIPPED, "dry-run enabled");
            return DeliveryDecision.block(DeliveryActionResult.DRY_RUN_SKIPPED, "dry-run enabled");
        }
        if (usedDeliverySlots >= maxDeliveries) {
            stopped = true;
            recordSkipped(jobInfo, action, DeliveryActionResult.LIMIT_REACHED, "maxDeliveries reached");
            return DeliveryDecision.block(DeliveryActionResult.LIMIT_REACHED, "maxDeliveries reached");
        }
        usedDeliverySlots++;
        return DeliveryDecision.allow();
    }

    /**
     * 只执行验证码和风控文本检查，不消耗投递额度。
     */
    public DeliveryDecision checkRisk(Page page, DeliveryJobInfo jobInfo, String action) {
        if (stopped) {
            return DeliveryDecision.block(DeliveryActionResult.RISK_STOPPED, "safety stop already triggered");
        }
        RiskHit hit = inspectRisk(page);
        if (hit == null) {
            return DeliveryDecision.allow();
        }
        if (hit.captcha && stopOnCaptcha) {
            stopped = true;
            recordSkipped(jobInfo, action, DeliveryActionResult.CAPTCHA_STOPPED, hit.reason);
            return DeliveryDecision.block(DeliveryActionResult.CAPTCHA_STOPPED, hit.reason);
        }
        if (!hit.captcha && stopOnRiskText) {
            stopped = true;
            recordSkipped(jobInfo, action, DeliveryActionResult.RISK_STOPPED, hit.reason);
            return DeliveryDecision.block(DeliveryActionResult.RISK_STOPPED, hit.reason);
        }
        return DeliveryDecision.allow();
    }

    /**
     * 记录一次确认成功的真实投递动作。
     */
    public void recordDelivered(DeliveryJobInfo jobInfo, String action, String reason) {
        emit(jobInfo, action, DeliveryActionResult.DELIVERED, reason);
    }

    /**
     * 记录一次可能已经发起但确认链路不完整的投递动作。
     */
    public void recordPossiblyDelivered(DeliveryJobInfo jobInfo, String action, String reason) {
        emit(jobInfo, action, DeliveryActionResult.POSSIBLY_DELIVERED, reason);
    }

    /**
     * 记录一次失败动作。
     */
    public void recordFailed(DeliveryJobInfo jobInfo, String action, String reason) {
        emit(jobInfo, action, DeliveryActionResult.FAILED, reason);
    }

    /**
     * 记录一次被安全门跳过的动作。
     */
    public void recordSkipped(DeliveryJobInfo jobInfo, String action, DeliveryActionResult result, String reason) {
        emit(jobInfo, action, result, reason);
    }

    /**
     * 记录达到限量并触发停止标记。
     */
    public void stopBecauseLimitReached(DeliveryJobInfo jobInfo, String action) {
        stopped = true;
        recordSkipped(jobInfo, action, DeliveryActionResult.LIMIT_REACHED, "maxDeliveries reached");
    }

    /**
     * 记录验证码或风控命中并触发停止标记。
     */
    public void stopBecauseRisk(DeliveryJobInfo jobInfo, String action, DeliveryActionResult result, String reason) {
        stopped = true;
        recordSkipped(jobInfo, action, result, reason);
    }

    /**
     * 返回是否处于 dry-run 模式。
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * 返回是否已经触发停止标记。
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * 返回当前还可发起的真实投递数量。
     */
    public int remainingDeliveries() {
        return Math.max(0, maxDeliveries - usedDeliverySlots);
    }

    private RiskHit inspectRisk(Page page) {
        if (page == null) {
            return null;
        }
        try {
            Object bodyText = page.evaluate("() => document.body ? (document.body.innerText || '') : ''");
            if (!(bodyText instanceof String text) || text.isBlank()) {
                return null;
            }
            String lower = text.toLowerCase();
            for (String word : CAPTCHA_TEXTS) {
                if (text.contains(word) || lower.contains(word.toLowerCase())) {
                    return new RiskHit(true, "captcha text matched: " + word);
                }
            }
            for (String word : RISK_TEXTS) {
                if (text.contains(word) || lower.contains(word.toLowerCase())) {
                    return new RiskHit(false, "risk text matched: " + word);
                }
            }
        } catch (Exception e) {
            log.debug("风险文本检测失败 platform={} action={}: {}", platform, "inspectRisk", e.getMessage());
        }
        return null;
    }

    private void emit(DeliveryJobInfo jobInfo, String action, DeliveryActionResult result, String reason) {
        DeliveryJobInfo safeJob = jobInfo == null ? DeliveryJobInfo.of("-", "-", "-") : jobInfo;
        String message = String.format(
                "ACTION_LOG platform=%s company=%s job=%s urlOrId=%s action=%s dryRun=%s result=%s reason=%s time=%s",
                platform,
                safeJob.getCompany(),
                safeJob.getJobName(),
                safeJob.getUrlOrId(),
                action == null ? "-" : action,
                dryRun,
                result,
                reason == null || reason.isBlank() ? "-" : reason.replace('\n', ' ').trim(),
                LocalDateTime.now()
        );
        log.info(message);
        if (progressEmitter != null) {
            progressEmitter.accept(message);
        }
    }

    private record RiskHit(boolean captcha, String reason) {
    }
}
