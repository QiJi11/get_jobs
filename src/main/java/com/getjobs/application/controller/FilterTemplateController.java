package com.getjobs.application.controller;

import com.getjobs.application.entity.FilterTemplateEntity;
import com.getjobs.application.service.FilterTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/filter-template")
@RequiredArgsConstructor
public class FilterTemplateController {
    private final FilterTemplateService filterTemplateService;

    @GetMapping
    public FilterTemplateEntity getDefaultTemplate() {
        return filterTemplateService.getDefaultTemplate();
    }

    @PutMapping
    public FilterTemplateEntity saveDefaultTemplate(@RequestBody FilterTemplateEntity template) {
        return filterTemplateService.saveDefaultTemplate(template == null ? new FilterTemplateEntity() : template);
    }

    @PostMapping("/apply")
    public Map<String, Object> applyTemplate(
            @RequestBody(required = false) FilterTemplateEntity template,
            @RequestParam(value = "platforms", required = false) String platforms
    ) {
        List<String> targetPlatforms = platforms == null || platforms.isBlank()
                ? List.of()
                : Arrays.stream(platforms.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        return Map.of(
                "success", true,
                "result", filterTemplateService.applyTemplate(template, targetPlatforms)
        );
    }
}
