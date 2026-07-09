package com.getjobs.worker.service;

import com.getjobs.worker.boss.BossConfig;
import com.getjobs.worker.job51.Job51Config;
import com.getjobs.worker.liepin.LiepinConfig;
import com.getjobs.worker.zhilian.ZhilianConfig;

public record DeliveryExecutionOptions(
        int maxDeliveries,
        boolean dryRun,
        boolean stopOnCaptcha,
        boolean stopOnRiskText
) {
    public static DeliveryExecutionOptions realApply(int maxDeliveries) {
        return new DeliveryExecutionOptions(Math.max(1, maxDeliveries), false, true, true);
    }

    public void applyTo(BossConfig config) {
        config.setDryRun(dryRun);
        config.setDebugger(dryRun);
        config.setMaxDeliveries(maxDeliveries);
        config.setStopOnCaptcha(stopOnCaptcha);
        config.setStopOnRiskText(stopOnRiskText);
    }

    public void applyTo(Job51Config config) {
        config.setDryRun(dryRun);
        config.setMaxDeliveries(maxDeliveries);
        config.setStopOnCaptcha(stopOnCaptcha);
        config.setStopOnRiskText(stopOnRiskText);
    }

    public void applyTo(LiepinConfig config) {
        config.setDryRun(dryRun);
        config.setMaxDeliveries(maxDeliveries);
        config.setStopOnCaptcha(stopOnCaptcha);
        config.setStopOnRiskText(stopOnRiskText);
    }

    public void applyTo(ZhilianConfig config) {
        config.setDryRun(dryRun);
        config.setMaxDeliveries(maxDeliveries);
        config.setStopOnCaptcha(stopOnCaptcha);
        config.setStopOnRiskText(stopOnRiskText);
        config.setAllowSimilarJobs(false);
    }
}
