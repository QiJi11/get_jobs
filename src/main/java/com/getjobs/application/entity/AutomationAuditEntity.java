package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_audit")
public class AutomationAuditEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    private String platform;

    @TableField("event_type")
    private String eventType;

    @TableField("target_company")
    private String targetCompany;

    @TableField("target_job")
    private String targetJob;

    @TableField("target_url")
    private String targetUrl;

    private String result;

    private String message;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
