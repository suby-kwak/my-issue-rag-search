package com.example.ragsearch.ingest.service;

import com.example.ragsearch.common.DocumentMetadata;
import com.example.ragsearch.ingest.dto.IngestRequest;
import com.example.ragsearch.ingest.dto.IngestResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class IngestService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

    public IngestService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public IngestResponse ingest(IngestRequest request) {
        deleteExistingChunks(request.source(), request.sourceId());

        Document document = new Document(request.content(), Map.of(
                DocumentMetadata.SOURCE, request.source(),
                DocumentMetadata.SOURCE_ID, request.sourceId(),
                DocumentMetadata.TITLE, request.title() == null ? "" : request.title(),
                DocumentMetadata.URL, request.url() == null ? "" : request.url()
        ));

        List<Document> chunks = splitter.split(List.of(document));
        vectorStore.add(chunks);

        return new IngestResponse(request.sourceId(), chunks.size());
    }

    private void deleteExistingChunks(String source, String sourceId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression existing = b.and(
                b.eq(DocumentMetadata.SOURCE, source),
                b.eq(DocumentMetadata.SOURCE_ID, sourceId)
        ).build();
        vectorStore.delete(existing);
    }
}
