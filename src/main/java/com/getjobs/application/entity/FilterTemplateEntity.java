package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("filter_template")
public class FilterTemplateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String keywords;
    private String city;
    private String salary;
    private String degree;
    private String experience;
    private String companyScale;
    private String industry;
    private String jobExcludeWords;
    private String companyBlacklist;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
