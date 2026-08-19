package com.isg.backend.violation.controller;

import com.isg.backend.modules.user.dto.UserResponse;
import com.isg.backend.modules.user.service.UserService;
import com.isg.backend.recording.application.port.PresignedPlaybackUrl;
import com.isg.backend.violation.service.ViolationMediaAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/violations")
public class ViolationMediaController {

    private final ViolationMediaAccessService mediaAccessService;
    private final UserService userService;

    public ViolationMediaController(
            ViolationMediaAccessService mediaAccessService,
            UserService userService
    ) {
        this.mediaAccessService =
                Objects.requireNonNull(
                        mediaAccessService
                );

        this.userService =
                Objects.requireNonNull(
                        userService
                );
    }

    @GetMapping("/{violationId}/clip-url")
    public ResponseEntity<MediaUrlResponse> createClipUrl(
            @PathVariable
            UUID violationId,
            Authentication authentication
    ) {
        UUID userId =
                resolveCurrentUserId(
                        authentication
                );

        PresignedPlaybackUrl presignedUrl =
                mediaAccessService.createClipUrl(
                        userId,
                        violationId
                );

        return ResponseEntity.ok(
                new MediaUrlResponse(
                        presignedUrl.url(),
                        presignedUrl.expiresAt()
                )
        );
    }

    private UUID resolveCurrentUserId(
            Authentication authentication
    ) {
        Objects.requireNonNull(
                authentication,
                "authentication must not be null"
        );

        UserResponse currentUser =
                userService.getMe(
                        authentication.getName()
                );

        if (currentUser.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user id must not be null"
            );
        }

        return currentUser.getId();
    }
}