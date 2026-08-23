package com.isg.backend.violation.service;

import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViolationClipGroupingProviderServiceTest {

    @Test
    void untrackedViolationDoesNotExposeSharedClipGroupingContext() {
        SpringDataViolationRepository repository =
                mock(SpringDataViolationRepository.class);

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        UUID violationId = UUID.randomUUID();

        when(repository.findById(violationId))
                .thenReturn(Optional.of(violation));

        when(violation.getCameraSessionId())
                .thenReturn(UUID.randomUUID());

        when(violation.getSubjectKey())
                .thenReturn("untracked");

        ViolationClipGroupingProviderService service =
                new ViolationClipGroupingProviderService(
                        repository
                );

        assertThat(
                service.findContext(violationId)
        ).isEmpty();
    }
}