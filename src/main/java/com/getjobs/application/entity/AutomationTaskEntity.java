package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_task")
public class AutomationTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * Comma-separated platform identifiers: boss, liepin, 51job, zhilian.
     */
    private String platforms;

    /**
     * DRY_RUN, REVIEW_THEN_APPLY, AUTO_APPLY.
     */
    private String mode;

    /**
     * QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, BLOCKED, USER_STOPPED.
     */
    private String status;

    @TableField("filter_template_id")
    private Long filterTemplateId;

    private String keywords;

    private String city;

    @TableField("max_applications")
    private Integer maxApplications;

    @TableField("max_daily_applications")
    private Integer maxDailyApplications;

    @TableField("allow_real_actions")
    private Integer allowRealActions;

    @TableField("review_approved")
    private Integer reviewApproved;

    @TableField("last_message")
    private String lastMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
