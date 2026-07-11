package com.example.ragsearch.query.dto;

import java.util.List;

public record QueryResponse(String answer, List<SourceRef> sources) {
}
