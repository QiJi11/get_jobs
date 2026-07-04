package com.getjobs.worker.safety;

/**
 * 投递安全日志中记录的岗位关键信息。
 */
public class DeliveryJobInfo {
    private final String company;
    private final String jobName;
    private final String urlOrId;

    private DeliveryJobInfo(String company, String jobName, String urlOrId) {
        this.company = normalize(company);
        this.jobName = normalize(jobName);
        this.urlOrId = normalize(urlOrId);
    }

    /**
     * 创建岗位信息快照。
     */
    public static DeliveryJobInfo of(String company, String jobName, String urlOrId) {
        return new DeliveryJobInfo(company, jobName, urlOrId);
    }

    /**
     * 返回公司名称。
     */
    public String getCompany() {
        return company;
    }

    /**
     * 返回岗位名称。
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * 返回岗位 URL 或平台 ID。
     */
    public String getUrlOrId() {
        return urlOrId;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "-" : value.replace('\n', ' ').trim();
    }
}
