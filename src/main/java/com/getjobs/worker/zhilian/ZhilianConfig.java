package com.getjobs.worker.zhilian;

import com.getjobs.worker.utils.JobUtils;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Objects;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class ZhilianConfig {
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

    /**
     * 是否允许投递智联弹窗中的相似职位。
     */
    private Boolean allowSimilarJobs = false;

    // 注意：已改为在 ZhilianJobService 中通过 ConfigService 构建配置
    // 保留空的 init 以兼容旧调用，但建议不要再使用
    @SneakyThrows
    public static ZhilianConfig init() {
        throw new UnsupportedOperationException("请在 ZhilianJobService 中通过 ConfigService 构建配置");
    }

}
