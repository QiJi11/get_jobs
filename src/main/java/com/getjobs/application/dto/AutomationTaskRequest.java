package com.getjobs.application.dto;

import lombok.Data;

import java.util.List;

@Data
public class AutomationTaskRequest {
    private String name;
    private List<String> platforms;
    private String mode;
    private Long filterTemplateId;
    private String keywords;
    private String city;
    private Integer maxApplications;
    private Integer maxDailyApplications;
    private Boolean allowRealActions;
}
