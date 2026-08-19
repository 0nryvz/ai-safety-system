package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ViolationRecoveryService {

    private final SpringDataViolationRepository violationRepository;

    public ViolationRecoveryService(
            SpringDataViolationRepository violationRepository
    ) {
        this.violationRepository = violationRepository;
    }

    @Transactional
    public int recoverInterruptedViolations() {

        List<ViolationJpaEntity> interruptedViolations =
                violationRepository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE,
                                ViolationLifecycleStatus.PREPARING
                        )
                );

        interruptedViolations.forEach(violation -> {
            violation.changeLifecycleStatus(
                    ViolationLifecycleStatus.PREPARING
            );

            violationRepository.save(violation);
        });

        return interruptedViolations.size();
    }
}