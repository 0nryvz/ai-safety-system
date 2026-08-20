package com.isg.backend.violation.application.recovery;

import com.isg.backend.violation.service.ViolationRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ViolationRecoveryStartupTest {

    @Test
    void triggersRecoveryWhenApplicationIsReady() {

        ViolationRecoveryService recoveryService =
                mock(ViolationRecoveryService.class);

        ViolationRecoveryStartup startup =
                new ViolationRecoveryStartup(
                        recoveryService
                );

        ApplicationReadyEvent event =
                mock(ApplicationReadyEvent.class);

        startup.recoverInterruptedViolations();

        verify(recoveryService)
                .recoverInterruptedViolations();
    }
}