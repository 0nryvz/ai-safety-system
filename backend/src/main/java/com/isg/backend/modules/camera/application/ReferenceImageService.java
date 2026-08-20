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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReferenceImageService {

    private static final long MAX_REFERENCE_IMAGE_SIZE_BYTES =
            5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final CameraRepository cameraRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final ObjectStoragePort objectStoragePort;

    @Transactional
    public void uploadReferenceImage(
            UUID cameraId,
            MultipartFile file
    ) {
        Camera camera =
                getAuthorizedCamera(cameraId);

        validateFile(file);

        String contentType =
                normalizedContentType(file);

        String objectKey =
                buildObjectKey(cameraId);

        try (BufferedInputStream inputStream =
                     new BufferedInputStream(
                             file.getInputStream()
                     )) {

            /*
             * İlk 12 byte JPEG, PNG ve WebP imza kontrolü
             * için yeterlidir.
             *
             * mark/reset sayesinde doğrulamadan sonra stream
             * tekrar başa alınır ve ObjectStoragePort dosyanın
             * tamamını yükler.
             */
            inputStream.mark(12);

            byte[] header =
                    inputStream.readNBytes(12);

            validateImageSignature(
                    header,
                    contentType
            );

            inputStream.reset();

            objectStoragePort.putObject(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    contentType
            );

        } catch (IOException ex) {
            throw new ObjectStorageException(
                    "Failed to read reference image upload",
                    ex
            );
        }

        /*
         * Aynı kamera için deterministic object key kullanılır.
         *
         * Sonraki upload aynı key üzerine yazıldığı için
         * reference image replacement sırasında yeni storage key
         * üretilmez.
         *
         * Object key frontend'e URL olarak dönülmez.
         */
        camera.setReferenceImageKey(objectKey);
        cameraRepository.save(camera);
    }

    @Transactional(readOnly = true)
    public ReferenceImageUrlResponse getReferenceImageUrl(
            UUID cameraId
    ) {
        Camera camera =
                getAuthorizedCamera(cameraId);

        String objectKey =
                camera.getReferenceImageKey();

        if (objectKey == null
                || objectKey.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Kamera için referans görüntüsü bulunamadı."
            );
        }

        PresignedObjectUrl presigned =
                objectStoragePort.createGetUrl(
                        objectKey
                );

        return new ReferenceImageUrlResponse(
                presigned.url(),
                presigned.expiresAt()
        );
    }

    private Camera getAuthorizedCamera(
            UUID cameraId
    ) {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Kimlik doğrulaması gereklidir."
            );
        }

        String email =
                authentication.getName();

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Kullanıcı bulunamadı."
                                )
                        );

        Camera camera =
                cameraRepository
                        .findById(cameraId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Kamera bulunamadı."
                                )
                        );

        if (camera.getDepartment() == null
                || !authorizationService.canAccessDepartment(
                user.getId(),
                camera.getDepartment().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bu kameraya erişim yetkiniz yok."
            );
        }

        return camera;
    }

    private void validateFile(
            MultipartFile file
    ) {
        if (file == null
                || file.isEmpty()
                || file.getSize() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Referans görüntüsü boş olamaz."
            );
        }

        if (file.getSize()
                > MAX_REFERENCE_IMAGE_SIZE_BYTES) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Referans görüntüsü en fazla 5 MB olabilir."
            );
        }

        String contentType =
                normalizedContentType(file);

        if (!ALLOWED_CONTENT_TYPES.contains(
                contentType
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yalnızca JPEG, PNG veya WebP görüntü yüklenebilir."
            );
        }
    }

    private void validateImageSignature(
            byte[] header,
            String contentType
    ) {
        boolean valid =
                switch (contentType) {
                    case "image/jpeg" ->
                            hasBytes(
                                    header,
                                    0,
                                    0xFF,
                                    0xD8,
                                    0xFF
                            );

                    case "image/png" ->
                            hasBytes(
                                    header,
                                    0,
                                    0x89,
                                    0x50,
                                    0x4E,
                                    0x47,
                                    0x0D,
                                    0x0A,
                                    0x1A,
                                    0x0A
                            );

                    case "image/webp" ->
                            hasBytes(
                                    header,
                                    0,
                                    'R',
                                    'I',
                                    'F',
                                    'F'
                            )
                                    && hasBytes(
                                    header,
                                    8,
                                    'W',
                                    'E',
                                    'B',
                                    'P'
                            );

                    default ->
                            false;
                };

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dosya içeriği belirtilen görüntü türüyle eşleşmiyor."
            );
        }
    }

    private boolean hasBytes(
            byte[] data,
            int offset,
            int... expected
    ) {
        if (data == null
                || data.length < offset + expected.length) {
            return false;
        }

        for (
                int index = 0;
                index < expected.length;
                index++
        ) {
            if ((data[offset + index] & 0xFF)
                    != expected[index]) {
                return false;
            }
        }

        return true;
    }

    private String normalizedContentType(
            MultipartFile file
    ) {
        String contentType =
                file.getContentType();

        if (contentType == null
                || contentType.isBlank()) {

            return "";
        }

        return contentType
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String buildObjectKey(
            UUID cameraId
    ) {
        return "cameras/"
                + cameraId
                + "/reference-image";
    }
}