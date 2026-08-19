package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.recording.application.RecordingMediaAccessService;
import com.isg.backend.recording.application.port.PresignedPlaybackUrl;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ViolationMediaAccessService {

    private final SpringDataViolationRepository violationRepository;
    private final AuthorizationService authorizationService;
    private final RecordingMediaAccessService recordingMediaAccessService;

    public ViolationMediaAccessService(
            SpringDataViolationRepository violationRepository,
            AuthorizationService authorizationService,
            RecordingMediaAccessService recordingMediaAccessService
    ) {
        this.violationRepository =
                Objects.requireNonNull(
                        violationRepository
                );

        this.authorizationService =
                Objects.requireNonNull(
                        authorizationService
                );

        this.recordingMediaAccessService =
                Objects.requireNonNull(
                        recordingMediaAccessService
                );
    }

    public PresignedPlaybackUrl createClipUrl(
            UUID userId,
            UUID violationId
    ) {
        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );

        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        ViolationJpaEntity violation =
                violationRepository
                        .findById(
                                violationId
                        )
                        .orElseThrow(
                                () ->
                                        new ViolationNotFoundException(
                                                violationId
                                        )
                        );

        boolean authorized =
                authorizationService
                        .canAccessDepartment(
                                userId,
                                violation.getDepartmentId()
                        );

        if (!authorized) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this violation."
            );
        }

        return recordingMediaAccessService
                .createClipUrl(
                        violationId
                );
    }
}