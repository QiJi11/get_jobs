package com.getjobs.application.service;

import com.getjobs.application.entity.AutomationAuditEntity;
import com.getjobs.application.entity.AutomationTaskEntity;
import com.getjobs.application.dto.AutomationStartRequest;
import com.getjobs.application.mapper.AutomationAuditMapper;
import com.getjobs.application.mapper.AutomationTaskMapper;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.DeliveryExecutionOptions;
import com.getjobs.worker.service.JobPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationServiceTest {
    @Mock
    AutomationTaskMapper taskMapper;
    @Mock
    AutomationAuditMapper auditMapper;
    @Mock
    JobPlatformService bossService;
    @Mock
    PlaywrightManager playwrightManager;

    AutomationService service;

    @BeforeEach
    void setUp() {
        when(auditMapper.insert(any(AutomationAuditEntity.class))).thenReturn(1);
        when(taskMapper.updateById(any(AutomationTaskEntity.class))).thenReturn(1);
        service = new AutomationService(taskMapper, auditMapper, List.of(bossService), playwrightManager);
    }

    @Test
    void dryRunCompletesWithoutCallingPlatformService() {
        AutomationTaskEntity task = task(1L, AutomationService.MODE_DRY_RUN, 0);
        when(taskMapper.selectById(1L)).thenReturn(task);

        AutomationTaskEntity result = service.startTask(1L);

        assertEquals(AutomationService.STATUS_COMPLETED, result.getStatus());
        assertTrue(result.getLastMessage().contains("Dry-run completed"));
        verify(bossService, never()).executeDelivery(anyProgressConsumer());
    }

    @Test
    void autoApplyRequiresExplicitRealActionFlag() {
        AutomationTaskEntity task = task(2L, AutomationService.MODE_AUTO_APPLY, 0);
        when(taskMapper.selectById(2L)).thenReturn(task);

        AutomationTaskEntity result = service.startTask(2L, confirmedStartRequest());

        assertEquals(AutomationService.STATUS_BLOCKED, result.getStatus());
        assertTrue(result.getLastMessage().contains("allowRealActions=true"));
        verify(bossService, never()).executeDelivery(anyProgressConsumer());

        ArgumentCaptor<AutomationAuditEntity> auditCaptor = ArgumentCaptor.forClass(AutomationAuditEntity.class);
        verify(auditMapper).insert(auditCaptor.capture());
        assertEquals("TASK_BLOCKED", auditCaptor.getValue().getEventType());
    }

    @Test
    void autoApplyRequiresConfirmationPhraseBeforeLoginCheck() {
        AutomationTaskEntity task = task(3L, AutomationService.MODE_AUTO_APPLY, 1);
        when(taskMapper.selectById(3L)).thenReturn(task);

        AutomationTaskEntity result = service.startTask(3L, new AutomationStartRequest());

        assertEquals(AutomationService.STATUS_BLOCKED, result.getStatus());
        assertTrue(result.getLastMessage().contains(AutomationService.AUTO_APPLY_CONFIRMATION_PHRASE));
        verify(playwrightManager, never()).isLoggedIn(anyString());
        verify(bossService, never()).executeDelivery(any(), any(DeliveryExecutionOptions.class));
    }

    @Test
    void autoApplyBlocksWhenPlatformIsNotLoggedIn() {
        AutomationTaskEntity task = task(4L, AutomationService.MODE_AUTO_APPLY, 1);
        when(taskMapper.selectById(4L)).thenReturn(task);
        when(playwrightManager.isLoggedIn("boss")).thenReturn(false);

        AutomationTaskEntity result = service.startTask(4L, confirmedStartRequest());

        assertEquals(AutomationService.STATUS_BLOCKED, result.getStatus());
        assertTrue(result.getLastMessage().contains("not logged in"));
        verify(bossService, never()).executeDelivery(any(), any(DeliveryExecutionOptions.class));
    }

    @Test
    void autoApplyRespectsOneApplicationDailyLimit() {
        AutomationTaskEntity task = task(5L, AutomationService.MODE_AUTO_APPLY, 1);
        when(taskMapper.selectById(5L)).thenReturn(task);
        when(playwrightManager.isLoggedIn("boss")).thenReturn(true);
        when(auditMapper.selectCount(any())).thenReturn(1L);

        AutomationTaskEntity result = service.startTask(5L, confirmedStartRequest());

        assertEquals(AutomationService.STATUS_RATE_LIMITED, result.getStatus());
        assertTrue(result.getLastMessage().contains("Daily application limit"));
        verify(bossService, never()).executeDelivery(any(), any(DeliveryExecutionOptions.class));
    }

    @Test
    void autoApplyDelegatesWithOneJobRealActionOptions() {
        AutomationTaskEntity task = task(6L, AutomationService.MODE_AUTO_APPLY, 1);
        when(taskMapper.selectById(6L)).thenReturn(task);
        when(playwrightManager.isLoggedIn("boss")).thenReturn(true);
        when(auditMapper.selectCount(any())).thenReturn(0L);
        when(bossService.getPlatformName()).thenReturn("boss");

        service.startTask(6L, confirmedStartRequest());

        ArgumentCaptor<DeliveryExecutionOptions> optionsCaptor = ArgumentCaptor.forClass(DeliveryExecutionOptions.class);
        verify(bossService, timeout(1000)).executeDelivery(anyProgressConsumer(), optionsCaptor.capture());
        assertEquals(1, optionsCaptor.getValue().maxDeliveries());
        assertFalse(optionsCaptor.getValue().dryRun());
        assertTrue(optionsCaptor.getValue().stopOnCaptcha());
        assertTrue(optionsCaptor.getValue().stopOnRiskText());
    }

    @Test
    void captchaProgressStopsRunningPlatformService() {
        AutomationTaskEntity task = task(7L, AutomationService.MODE_AUTO_APPLY, 1);
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(playwrightManager.isLoggedIn("boss")).thenReturn(true);
        when(auditMapper.selectCount(any())).thenReturn(0L);
        when(bossService.getPlatformName()).thenReturn("boss");
        when(bossService.isRunning()).thenReturn(true);
        doAnswer(invocation -> {
            Consumer<JobProgressMessage> callback = invocation.getArgument(0);
            callback.accept(JobProgressMessage.warning("boss", "检测到验证码"));
            return null;
        }).when(bossService).executeDelivery(anyProgressConsumer(), any(DeliveryExecutionOptions.class));

        service.startTask(7L, confirmedStartRequest());

        verify(bossService, timeout(1000).atLeastOnce()).stopDelivery();
        verify(taskMapper, atLeastOnce()).updateById(task);
        assertEquals("BLOCKED_BY_CAPTCHA", task.getStatus());
    }

    private AutomationTaskEntity task(Long id, String mode, int allowRealActions) {
        AutomationTaskEntity task = new AutomationTaskEntity();
        task.setId(id);
        task.setName("test");
        task.setPlatforms("boss");
        task.setMode(mode);
        task.setStatus(AutomationService.STATUS_QUEUED);
        task.setMaxApplications(1);
        task.setMaxDailyApplications(1);
        task.setAllowRealActions(allowRealActions);
        task.setReviewApproved(0);
        return task;
    }

    private AutomationStartRequest confirmedStartRequest() {
        AutomationStartRequest request = new AutomationStartRequest();
        request.setConfirmationPhrase(AutomationService.AUTO_APPLY_CONFIRMATION_PHRASE);
        return request;
    }

    private Consumer<JobProgressMessage> anyProgressConsumer() {
        return any();
    }
}
