package com.getjobs.worker.safety;

/**
 * 安全门对一次投递动作的放行或拦截结果。
 */
public class DeliveryDecision {
    private final boolean allowed;
    private final DeliveryActionResult result;
    private final String reason;

    private DeliveryDecision(boolean allowed, DeliveryActionResult result, String reason) {
        this.allowed = allowed;
        this.result = result;
        this.reason = reason;
    }

    /**
     * 创建放行动作的决策。
     */
    public static DeliveryDecision allow() {
        return new DeliveryDecision(true, null, "");
    }

    /**
     * 创建拦截动作的决策。
     */
    public static DeliveryDecision block(DeliveryActionResult result, String reason) {
        return new DeliveryDecision(false, result, reason);
    }

    /**
     * 返回是否允许继续执行真实动作。
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * 返回拦截结果；放行时为 null。
     */
    public DeliveryActionResult getResult() {
        return result;
    }

    /**
     * 返回放行或拦截原因。
     */
    public String getReason() {
        return reason;
    }
}
