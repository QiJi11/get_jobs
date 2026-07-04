package com.getjobs.worker.safety;

/**
 * 自动投递动作的固定审计结果枚举。
 */
public enum DeliveryActionResult {
    DRY_RUN_SKIPPED,
    DELIVERED,
    LIMIT_REACHED,
    RISK_STOPPED,
    CAPTCHA_STOPPED,
    FAILED,
    POSSIBLY_DELIVERED
}
