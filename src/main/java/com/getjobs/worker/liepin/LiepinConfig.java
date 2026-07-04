package com.getjobs.worker.liepin;

import lombok.Data;

import java.util.List;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class LiepinConfig {
    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市编码
     */
    private String cityCode;

    /**
     * 薪资范围
     */
    private String salary;

    /**
     * Dry-run模式：只记录候选，不执行真实投递。
     */
    private Boolean dryRun = true;

    /**
     * 单次任务最多允许的真实投递数。
     */
    private Integer maxDeliveries = 1;

    /**
     * 命中验证码文本时停止任务。
     */
    private Boolean stopOnCaptcha = true;

    /**
     * 命中风控或频控文本时停止任务。
     */
    private Boolean stopOnRiskText = true;
}
