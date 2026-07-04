package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.BossConfigEntity;
import com.getjobs.application.entity.FilterTemplateEntity;
import com.getjobs.application.entity.Job51ConfigEntity;
import com.getjobs.application.entity.LiepinConfigEntity;
import com.getjobs.application.entity.ZhilianConfigEntity;
import com.getjobs.application.mapper.FilterTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FilterTemplateService {
    private final FilterTemplateMapper filterTemplateMapper;
    private final BossService bossService;
    private final LiepinService liepinService;
    private final Job51Service job51Service;
    private final ZhilianService zhilianService;

    public FilterTemplateEntity getDefaultTemplate() {
        FilterTemplateEntity existing = filterTemplateMapper.selectOne(new QueryWrapper<FilterTemplateEntity>()
                .orderByAsc("id")
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        FilterTemplateEntity created = new FilterTemplateEntity();
        created.setName("默认筛选模板");
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        filterTemplateMapper.insert(created);
        return created;
    }

    public FilterTemplateEntity saveDefaultTemplate(FilterTemplateEntity incoming) {
        FilterTemplateEntity existing = getDefaultTemplate();
        existing.setName(nonBlank(incoming.getName(), "默认筛选模板"));
        existing.setKeywords(incoming.getKeywords());
        existing.setCity(incoming.getCity());
        existing.setSalary(incoming.getSalary());
        existing.setDegree(incoming.getDegree());
        existing.setExperience(incoming.getExperience());
        existing.setCompanyScale(incoming.getCompanyScale());
        existing.setIndustry(incoming.getIndustry());
        existing.setJobExcludeWords(incoming.getJobExcludeWords());
        existing.setCompanyBlacklist(incoming.getCompanyBlacklist());
        existing.setUpdatedAt(LocalDateTime.now());
        filterTemplateMapper.updateById(existing);
        return existing;
    }

    public Map<String, Object> applyTemplate(FilterTemplateEntity incoming, List<String> platforms) {
        FilterTemplateEntity template = incoming == null ? getDefaultTemplate() : saveDefaultTemplate(incoming);
        List<String> targetPlatforms = platforms == null || platforms.isEmpty()
                ? List.of("boss", "liepin", "51job", "zhilian")
                : platforms;
        Map<String, Object> result = new LinkedHashMap<>();
        for (String platform : targetPlatforms) {
            result.put(platform, applyToPlatform(template, platform));
        }
        return result;
    }

    private Map<String, Object> applyToPlatform(FilterTemplateEntity template, String platform) {
        Map<String, Object> status = new LinkedHashMap<>();
        List<String> applied = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        switch (platform) {
            case "boss" -> applyBoss(template, applied);
            case "liepin" -> applyLiepin(template, applied, unsupported);
            case "51job" -> apply51job(template, applied, unsupported);
            case "zhilian" -> applyZhilian(template, applied, unsupported);
            default -> unsupported.add("platform");
        }
        status.put("applied", applied);
        status.put("unsupported", unsupported);
        return status;
    }

    private void applyBoss(FilterTemplateEntity template, List<String> applied) {
        BossConfigEntity config = new BossConfigEntity();
        if (hasText(template.getKeywords())) {
            config.setKeywords(toBracketListString(template.getKeywords()));
            applied.add("keywords");
        }
        if (hasText(template.getCity())) {
            config.setCityCode(template.getCity());
            applied.add("city");
        }
        if (hasText(template.getSalary())) {
            config.setSalary(toBracketListString(template.getSalary()));
            applied.add("salary");
        }
        if (hasText(template.getDegree())) {
            config.setDegree(toBracketListString(template.getDegree()));
            applied.add("degree");
        }
        if (hasText(template.getExperience())) {
            config.setExperience(toBracketListString(template.getExperience()));
            applied.add("experience");
        }
        if (hasText(template.getCompanyScale())) {
            config.setScale(toBracketListString(template.getCompanyScale()));
            applied.add("companyScale");
        }
        if (hasText(template.getIndustry())) {
            config.setIndustry(toBracketListString(template.getIndustry()));
            applied.add("industry");
        }
        bossService.saveOrUpdateFirstSelective(config);
        addBossBlacklist("job", template.getJobExcludeWords(), applied, "jobExcludeWords");
        addBossBlacklist("company", template.getCompanyBlacklist(), applied, "companyBlacklist");
    }

    private void applyLiepin(FilterTemplateEntity template, List<String> applied, List<String> unsupported) {
        LiepinConfigEntity config = new LiepinConfigEntity();
        if (hasText(template.getKeywords())) {
            config.setKeywords(toBracketListString(template.getKeywords()));
            applied.add("keywords");
        }
        if (hasText(template.getCity())) {
            config.setCity(template.getCity());
            applied.add("city");
        }
        if (hasText(template.getSalary())) {
            config.setSalaryCode(template.getSalary());
            applied.add("salary");
        }
        addUnsupported(template, unsupported, "degree", "experience", "companyScale", "industry", "jobExcludeWords", "companyBlacklist");
        liepinService.saveOrUpdateFirstSelective(config);
    }

    private void apply51job(FilterTemplateEntity template, List<String> applied, List<String> unsupported) {
        Job51ConfigEntity config = new Job51ConfigEntity();
        if (hasText(template.getKeywords())) {
            config.setKeywords(toBracketListString(template.getKeywords()));
            applied.add("keywords");
        }
        if (hasText(template.getCity())) {
            config.setJobArea(toBracketListString(template.getCity()));
            applied.add("city");
        }
        if (hasText(template.getSalary())) {
            config.setSalary(toBracketListString(template.getSalary()));
            applied.add("salary");
        }
        addUnsupported(template, unsupported, "degree", "experience", "companyScale", "industry", "jobExcludeWords", "companyBlacklist");
        job51Service.saveOrUpdateFirstSelective(config);
    }

    private void applyZhilian(FilterTemplateEntity template, List<String> applied, List<String> unsupported) {
        ZhilianConfigEntity config = new ZhilianConfigEntity();
        if (hasText(template.getKeywords())) {
            config.setKeywords(toBracketListString(template.getKeywords()));
            applied.add("keywords");
        }
        if (hasText(template.getCity())) {
            config.setCityCode(firstValue(template.getCity()));
            applied.add("city");
        }
        if (hasText(template.getSalary())) {
            config.setSalary(firstValue(template.getSalary()));
            applied.add("salary");
        }
        addUnsupported(template, unsupported, "degree", "experience", "companyScale", "industry", "jobExcludeWords", "companyBlacklist");
        zhilianService.saveOrUpdateFirstSelective(config);
    }

    private void addBossBlacklist(String type, String values, List<String> applied, String fieldName) {
        List<String> list = parseList(values);
        if (list.isEmpty()) {
            return;
        }
        for (String value : list) {
            bossService.addBlacklist(type, value);
        }
        applied.add(fieldName);
    }

    private void addUnsupported(FilterTemplateEntity template, List<String> unsupported, String... fields) {
        for (String field : fields) {
            String value = switch (field) {
                case "degree" -> template.getDegree();
                case "experience" -> template.getExperience();
                case "companyScale" -> template.getCompanyScale();
                case "industry" -> template.getIndustry();
                case "jobExcludeWords" -> template.getJobExcludeWords();
                case "companyBlacklist" -> template.getCompanyBlacklist();
                default -> null;
            };
            if (hasText(value)) {
                unsupported.add(field);
            }
        }
    }

    private String firstValue(String raw) {
        List<String> values = parseList(raw);
        return values.isEmpty() ? raw : values.get(0);
    }

    private String toBracketListString(String raw) {
        List<String> values = parseList(raw);
        return values.isEmpty() ? null : "[" + String.join(",", values) + "]";
    }

    private List<String> parseList(String raw) {
        if (!hasText(raw)) {
            return List.of();
        }
        String normalized = raw.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return Arrays.stream(normalized.split("[,，\\n]"))
                .map(String::trim)
                .map(s -> s.replaceAll("^['\"]|['\"]$", ""))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nonBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }
}
