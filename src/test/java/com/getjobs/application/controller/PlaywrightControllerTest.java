package com.getjobs.application.controller;

import com.getjobs.worker.manager.PlaywrightManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaywrightControllerTest {
    @Mock
    PlaywrightManager playwrightManager;

    PlaywrightController controller;

    @BeforeEach
    void setUp() {
        controller = new PlaywrightController(playwrightManager);
    }

    @Test
    void bossDebugReturnsReadOnlyManagerSnapshot() {
        Map<String, Object> debug = Map.of(
                "hasBossPage", true,
                "loginStateSource", "persistent_profile"
        );
        when(playwrightManager.getBossDebugInfo()).thenReturn(debug);

        ResponseEntity<Map<String, Object>> response = controller.getBossDebug();

        assertSame(debug, response.getBody());
        verify(playwrightManager).getBossDebugInfo();
    }

    @Test
    void bossLoginCheckRefreshesLoginStatusOnce() {
        Map<String, Object> debug = Map.of("bossLoggedIn", true);
        when(playwrightManager.refreshBossLoginStatus()).thenReturn(true);
        when(playwrightManager.getLoginStateSource("boss")).thenReturn("persistent_profile");
        when(playwrightManager.getBossDebugInfo()).thenReturn(debug);

        ResponseEntity<Map<String, Object>> response = controller.checkBossLogin();

        assertTrue((Boolean) response.getBody().get("success"));
        assertTrue((Boolean) response.getBody().get("bossLoggedIn"));
        assertEquals("persistent_profile", response.getBody().get("loginStateSource"));
        assertSame(debug, response.getBody().get("debug"));
        verify(playwrightManager).refreshBossLoginStatus();
    }

    @Test
    void bossResetOnlyDelegatesToBossContextReset() {
        Map<String, Object> resetResult = Map.of(
                "success", true,
                "profileDeleted", false
        );
        when(playwrightManager.resetBossContext()).thenReturn(resetResult);

        ResponseEntity<Map<String, Object>> response = controller.resetBossContext();

        assertSame(resetResult, response.getBody());
        verify(playwrightManager).resetBossContext();
    }
}
