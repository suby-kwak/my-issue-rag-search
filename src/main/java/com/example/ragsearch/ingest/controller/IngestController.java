package com.example.ragsearch.ingest.controller;

import com.example.ragsearch.ingest.dto.IngestRequest;
import com.example.ragsearch.ingest.dto.IngestResponse;
import com.example.ragsearch.ingest.service.IngestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/api/ingest")
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        IngestResponse response = ingestService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
