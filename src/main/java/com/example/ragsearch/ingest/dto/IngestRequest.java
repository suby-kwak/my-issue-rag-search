package com.example.ragsearch.ingest.dto;

import jakarta.validation.constraints.NotBlank;

public record IngestRequest(
        @NotBlank String source,
        @NotBlank String sourceId,
        String title,
        String url,
        @NotBlank String content
) {
}
