package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job51_config")
public class Job51ConfigEntity {
    @TableId(type = IdType.AUTO)
    /** 主键ID */
    private Long id;

    /** 搜索关键词（逗号或括号列表，例如 "[Java,后端]" 或 "Java,后端"） */
    private String keywords;

    /** 城市区域（中文名或代码，列表字符串） */
    private String jobArea;

    /** 薪资范围（中文名或代码，列表字符串） */
    private String salary;

    /** Dry-run模式：只审计候选，不执行真实投递 */
    private Boolean dryRun;

    /** 单次任务最多允许的真实投递数 */
    private Integer maxDeliveries;

    /** 命中验证码文本时停止任务 */
    private Boolean stopOnCaptcha;

    /** 命中风控或频控文本时停止任务 */
    private Boolean stopOnRiskText;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
