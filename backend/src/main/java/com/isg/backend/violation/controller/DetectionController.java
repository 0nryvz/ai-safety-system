package com.isg.backend.violation.controller;

import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.violation.service.DetectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/detections")
public class DetectionController {

    private final DetectionService detectionService;

    public DetectionController(DetectionService detectionService) {
        this.detectionService = detectionService;
    }

    @PostMapping
    public ResponseEntity<Void> receiveDetection(
            @Valid @RequestBody DetectionRequest request
    ) {
        detectionService.process(request);

        return ResponseEntity.accepted().build();
    }
}
