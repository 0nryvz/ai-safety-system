package com.isg.backend.violation.application.recovery;

import com.isg.backend.violation.service.ViolationRecoveryService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ViolationRecoveryStartup {

    private final ViolationRecoveryService recoveryService;

    public ViolationRecoveryStartup(
            ViolationRecoveryService recoveryService
    ) {
        this.recoveryService = recoveryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedViolations() {
        recoveryService.recoverInterruptedViolations();
    }
}