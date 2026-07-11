package com.example.ragsearch.query.controller;

import com.example.ragsearch.query.dto.QueryRequest;
import com.example.ragsearch.query.dto.QueryResponse;
import com.example.ragsearch.query.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/api/query")
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        return queryService.query(request);
    }
}
