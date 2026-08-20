package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.ReferenceImageUrlResponse;
import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.shared.storage.ObjectStorageException;
import com.isg.backend.shared.storage.ObjectStoragePort;
import com.isg.backend.shared.storage.PresignedObjectUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceImageServiceTest {

    private static final String USER_EMAIL =
            "be2@test.local";

    private static final long MAX_SIZE_BYTES =
            5L * 1024L * 1024L;

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectStoragePort objectStoragePort;

    @Mock
    private User user;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Camera camera;

    private ReferenceImageService referenceImageService;

    private UUID cameraId;
    private UUID userId;
    private UUID departmentId;

    @BeforeEach
    void setUp() {
        referenceImageService =
                new ReferenceImageService(
                        cameraRepository,
                        authorizationService,
                        userRepository,
                        objectStoragePort
                );

        cameraId = UUID.randomUUID();
        userId = UUID.randomUUID();
        departmentId = UUID.randomUUID();

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                USER_EMAIL,
                                "unused",
                                List.of()
                        )
                );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validImageIsUploadedAndReferenceKeyIsPersisted() {
        stubAuthorizedCamera();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "reference.png",
                        "image/png",
                        validPngBytes()
                );

        String expectedKey =
                "cameras/"
                        + cameraId
                        + "/reference-image";

        referenceImageService.uploadReferenceImage(
                cameraId,
                file
        );

        verify(objectStoragePort)
                .putObject(
                        eq(expectedKey),
                        any(InputStream.class),
                        eq(file.getSize()),
                        eq("image/png")
                );

        verify(camera)
                .setReferenceImageKey(expectedKey);

        verify(cameraRepository)
                .save(camera);
    }

    @Test
    void replacementUsesSameDeterministicObjectKey() {
        stubAuthorizedCamera();

        MockMultipartFile first =
                new MockMultipartFile(
                        "file",
                        "first.jpg",
                        "image/jpeg",
                        validJpegBytes()
                );

        MockMultipartFile second =
                new MockMultipartFile(
                        "file",
                        "second.jpg",
                        "image/jpeg",
                        validJpegBytes()
                );

        String expectedKey =
                "cameras/"
                        + cameraId
                        + "/reference-image";

        referenceImageService.uploadReferenceImage(
                cameraId,
                first
        );

        referenceImageService.uploadReferenceImage(
                cameraId,
                second
        );

        verify(
                objectStoragePort,
                times(2)
        )
                .putObject(
                        eq(expectedKey),
                        any(InputStream.class),
                        anyLong(),
                        eq("image/jpeg")
                );

        verify(
                camera,
                times(2)
        )
                .setReferenceImageKey(
                        expectedKey
                );

        verify(
                cameraRepository,
                times(2)
        )
                .save(camera);
    }

    @Test
    void emptyImageReturnsBadRequest() {
        stubAuthorizedCamera();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "empty.png",
                        "image/png",
                        new byte[0]
                );

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .uploadReferenceImage(
                                        cameraId,
                                        file
                                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.BAD_REQUEST
                                        )
                );

        verify(
                objectStoragePort,
                never()
        )
                .putObject(
                        anyString(),
                        any(InputStream.class),
                        anyLong(),
                        anyString()
                );

        verify(
                cameraRepository,
                never()
        )
                .save(any(Camera.class));
    }

    @Test
    void oversizedImageReturnsBadRequest() {
        stubAuthorizedCamera();

        MultipartFile file =
                mock(MultipartFile.class);

        when(file.isEmpty())
                .thenReturn(false);

        when(file.getSize())
                .thenReturn(
                        MAX_SIZE_BYTES + 1
                );

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .uploadReferenceImage(
                                        cameraId,
                                        file
                                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.BAD_REQUEST
                                        )
                );

        verify(
                objectStoragePort,
                never()
        )
                .putObject(
                        anyString(),
                        any(InputStream.class),
                        anyLong(),
                        anyString()
                );

        verify(
                cameraRepository,
                never()
        )
                .save(any(Camera.class));
    }

    @Test
    void unsupportedContentTypeReturnsBadRequest() {
        stubAuthorizedCamera();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "reference.txt",
                        "text/plain",
                        new byte[]{1, 2, 3}
                );

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .uploadReferenceImage(
                                        cameraId,
                                        file
                                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.BAD_REQUEST
                                        )
                );

        verify(
                objectStoragePort,
                never()
        )
                .putObject(
                        anyString(),
                        any(InputStream.class),
                        anyLong(),
                        anyString()
                );

        verify(
                cameraRepository,
                never()
        )
                .save(any(Camera.class));
    }

    @Test
    void spoofedImageContentTypeReturnsBadRequest() {
        stubAuthorizedCamera();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "fake.png",
                        "image/png",
                        new byte[]{
                                'n',
                                'o',
                                't',
                                '-',
                                'a',
                                'n',
                                '-',
                                'i',
                                'm',
                                'a',
                                'g',
                                'e'
                        }
                );

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .uploadReferenceImage(
                                        cameraId,
                                        file
                                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> {
                            assertThat(
                                    ex.getStatusCode()
                            )
                                    .isEqualTo(
                                            HttpStatus.BAD_REQUEST
                                    );

                            assertThat(
                                    ex.getReason()
                            )
                                    .isEqualTo(
                                            "Dosya içeriği belirtilen görüntü türüyle eşleşmiyor."
                                    );
                        }
                );

        verify(
                objectStoragePort,
                never()
        )
                .putObject(
                        anyString(),
                        any(InputStream.class),
                        anyLong(),
                        anyString()
                );

        verify(
                cameraRepository,
                never()
        )
                .save(any(Camera.class));
    }

    @Test
    void unauthorizedDepartmentCannotUploadReferenceImage() {
        stubUnauthorizedCamera();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "reference.png",
                        "image/png",
                        validPngBytes()
                );

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .uploadReferenceImage(
                                        cameraId,
                                        file
                                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.FORBIDDEN
                                        )
                );

        verify(
                objectStoragePort,
                never()
        )
                .putObject(
                        anyString(),
                        any(InputStream.class),
                        anyLong(),
                        anyString()
                );

        verify(
                cameraRepository,
                never()
        )
                .save(any(Camera.class));
    }

    @Test
    void missingCameraReturnsNotFound() {
        when(userRepository.findByEmail(
                USER_EMAIL
        ))
                .thenReturn(
                        Optional.of(user)
                );

        when(cameraRepository.findById(
                cameraId
        ))
                .thenReturn(
                        Optional.empty()
                );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "reference.png",
                        "image/png",
                        validPngBytes()
                );

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .uploadReferenceImage(
                                        cameraId,
                                        file
                                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.NOT_FOUND
                                        )
                );

        verifyNoInteractions(
                objectStoragePort
        );
    }

    @Test
    void storageFailureDoesNotPersistReferenceImageKey() {
        stubAuthorizedCamera();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "reference.webp",
                        "image/webp",
                        validWebpBytes()
                );

        doThrow(
                new ObjectStorageException(
                        "storage failed",
                        new RuntimeException(
                                "boom"
                        )
                )
        )
                .when(objectStoragePort)
                .putObject(
                        anyString(),
                        any(InputStream.class),
                        anyLong(),
                        anyString()
                );

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .uploadReferenceImage(
                                        cameraId,
                                        file
                                )
        )
                .isInstanceOf(
                        ObjectStorageException.class
                );

        verify(
                camera,
                never()
        )
                .setReferenceImageKey(
                        anyString()
                );

        verify(
                cameraRepository,
                never()
        )
                .save(any(Camera.class));
    }

    @Test
    void authorizedUserGetsPresignedReferenceImageUrl() {
        stubAuthorizedCamera();

        String objectKey =
                "cameras/"
                        + cameraId
                        + "/reference-image";

        String url =
                "https://storage.test/"
                        + objectKey
                        + "?signature=test";

        Instant expiresAt =
                Instant.parse(
                        "2026-08-20T07:30:00Z"
                );

        when(camera.getReferenceImageKey())
                .thenReturn(objectKey);

        when(objectStoragePort.createGetUrl(
                objectKey
        ))
                .thenReturn(
                        new PresignedObjectUrl(
                                url,
                                expiresAt
                        )
                );

        ReferenceImageUrlResponse response =
                referenceImageService
                        .getReferenceImageUrl(
                                cameraId
                        );

        assertThat(response.url())
                .isEqualTo(url);

        assertThat(response.expiresAt())
                .isEqualTo(expiresAt);

        verify(objectStoragePort)
                .createGetUrl(objectKey);
    }

    @Test
    void missingReferenceImageReturnsNotFound() {
        stubAuthorizedCamera();

        when(camera.getReferenceImageKey())
                .thenReturn(null);

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .getReferenceImageUrl(
                                        cameraId
                                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.NOT_FOUND
                                        )
                );

        verify(
                objectStoragePort,
                never()
        )
                .createGetUrl(
                        anyString()
                );
    }

    @Test
    void unauthorizedDepartmentCannotGetReferenceImageUrl() {
        stubUnauthorizedCamera();

        assertThatThrownBy(
                () ->
                        referenceImageService
                                .getReferenceImageUrl(
                                        cameraId
                                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.FORBIDDEN
                                        )
                );

        verify(
                objectStoragePort,
                never()
        )
                .createGetUrl(
                        anyString()
                );
    }

    private byte[] validPngBytes() {
        return new byte[]{
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x01,
                0x02,
                0x03,
                0x04
        };
    }

    private byte[] validJpegBytes() {
        return new byte[]{
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                (byte) 0xE0,
                0x00,
                0x10,
                0x4A,
                0x46,
                0x49,
                0x46,
                0x00,
                0x01
        };
    }

    private byte[] validWebpBytes() {
        return new byte[]{
                'R',
                'I',
                'F',
                'F',
                0x00,
                0x00,
                0x00,
                0x00,
                'W',
                'E',
                'B',
                'P'
        };
    }

    private void stubCurrentUser() {
        when(userRepository.findByEmail(
                USER_EMAIL
        ))
                .thenReturn(
                        Optional.of(user)
                );

        when(user.getId())
                .thenReturn(userId);
    }

    private void stubAuthorizedCamera() {
        stubCurrentUser();

        when(cameraRepository.findById(
                cameraId
        ))
                .thenReturn(
                        Optional.of(camera)
                );

        when(camera.getDepartment().getId())
                .thenReturn(departmentId);

        when(authorizationService
                .canAccessDepartment(
                        userId,
                        departmentId
                ))
                .thenReturn(true);
    }

    private void stubUnauthorizedCamera() {
        stubCurrentUser();

        when(cameraRepository.findById(
                cameraId
        ))
                .thenReturn(
                        Optional.of(camera)
                );

        when(camera.getDepartment().getId())
                .thenReturn(departmentId);

        when(authorizationService
                .canAccessDepartment(
                        userId,
                        departmentId
                ))
                .thenReturn(false);
    }
}