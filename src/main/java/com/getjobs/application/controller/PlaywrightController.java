package com.getjobs.application.controller;

import com.getjobs.worker.manager.PlaywrightManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Playwright管理控制器
 * 用于测试和管理Playwright实例
 */
@RestController
@RequestMapping("/api/playwright")
public class PlaywrightController {

    private final PlaywrightManager playwrightManager;

    public PlaywrightController(PlaywrightManager playwrightManager) {
        this.playwrightManager = playwrightManager;
    }

    /**
     * 获取Playwright状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("initialized", playwrightManager.isInitialized());
        status.put("cdpPort", playwrightManager.getCdpPort());
        status.put("bossCdpPort", playwrightManager.getBossCdpPort());
        status.put("hasBossPage", playwrightManager.getBossPage() != null);
        status.put("hasBrowser", playwrightManager.hasSharedBrowser() || playwrightManager.getBossContext() != null);
        status.put("platformInitialized", playwrightManager.getPlatformInitializationStatus());
        status.put("bossLoggedIn", playwrightManager.isLoggedIn("boss"));
        status.put("loginStateSources", Map.of(
                "boss", playwrightManager.getLoginStateSource("boss"),
                "liepin", playwrightManager.getLoginStateSource("liepin"),
                "51job", playwrightManager.getLoginStateSource("51job"),
                "zhilian", playwrightManager.getLoginStateSource("zhilian")
        ));

        return ResponseEntity.ok(status);
    }

    /**
     * 手动初始化浏览器或指定平台。
     * 不带 platform 只启动 Playwright 引擎；platform=all 才会初始化全部平台。
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init(@RequestParam(value = "platform", required = false) String platform) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (platform == null || platform.isBlank()) {
                playwrightManager.init();
            } else if ("all".equalsIgnoreCase(platform)) {
                playwrightManager.initAllPlatforms();
            } else {
                playwrightManager.initPlatform(platform);
            }
            result.put("success", true);
            result.put("initialized", playwrightManager.isInitialized());
            result.put("platformInitialized", playwrightManager.getPlatformInitializationStatus());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 测试Boss导航功能
     */
    @GetMapping("/test-navigate")
    public ResponseEntity<Map<String, String>> testNavigate() {
        try {
            playwrightManager.initPlatform("boss");
            playwrightManager.getBossPage().navigate("https://www.zhipin.com");
            String title = playwrightManager.getBossPage().title();

            Map<String, String> result = new HashMap<>();
            result.put("success", "true");
            result.put("title", title);
            result.put("url", playwrightManager.getBossPage().url());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("success", "false");
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
