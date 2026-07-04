package com.getjobs.worker.safety;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeliverySafety 的 dry-run 和限量行为测试。
 */
class DeliverySafetyTest {

    @Test
    void dryRunBlocksRealActionWithoutStoppingTask() {
        List<String> logs = new ArrayList<>();
        DeliverySafety safety = DeliverySafety.create("test", true, 1, true, true, logs::add);

        DeliveryDecision decision = safety.canProceed(null, DeliveryJobInfo.of("ACME", "Engineer", "1"), "apply");

        assertFalse(decision.isAllowed());
        assertEquals(DeliveryActionResult.DRY_RUN_SKIPPED, decision.getResult());
        assertFalse(safety.isStopped());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("result=DRY_RUN_SKIPPED"));
    }

    @Test
    void maxDeliveriesBlocksSecondRealAction() {
        List<String> logs = new ArrayList<>();
        DeliverySafety safety = DeliverySafety.create("test", false, 1, true, true, logs::add);

        DeliveryDecision first = safety.canProceed(null, DeliveryJobInfo.of("ACME", "Engineer", "1"), "apply");
        DeliveryDecision second = safety.canProceed(null, DeliveryJobInfo.of("Beta", "Engineer", "2"), "apply");

        assertTrue(first.isAllowed());
        assertFalse(second.isAllowed());
        assertEquals(DeliveryActionResult.LIMIT_REACHED, second.getResult());
        assertTrue(safety.isStopped());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("result=LIMIT_REACHED"));
    }
}
