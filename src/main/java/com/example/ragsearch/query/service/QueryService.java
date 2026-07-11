package com.example.ragsearch.query.service;

import com.example.ragsearch.common.DocumentMetadata;
import com.example.ragsearch.query.dto.QueryRequest;
import com.example.ragsearch.query.dto.QueryResponse;
import com.example.ragsearch.query.dto.SourceRef;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryService {

    private static final String PROMPT_TEMPLATE = """
            Answer the question using only the context below. If the context does not contain
            the answer, say you don't have a record of it. Do not use outside knowledge.

            Context:
            %s

            Question: %s
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final int defaultTopK;

    public QueryService(VectorStore vectorStore, ChatModel chatModel,
                         @Value("${rag.query.top-k:5}") int defaultTopK) {
        this.vectorStore = vectorStore;
        this.chatClient = ChatClient.create(chatModel);
        this.defaultTopK = defaultTopK;
    }

    public QueryResponse query(QueryRequest request) {
        int topK = request.topK() != null ? request.topK() : defaultTopK;

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(request.question()).topK(topK).build());

        String context = String.join("\n---\n", results.stream().map(Document::getText).toList());

        String answer = chatClient.prompt()
                .user(PROMPT_TEMPLATE.formatted(context, request.question()))
                .call()
                .content();

        List<SourceRef> sources = results.stream()
                .map(doc -> new SourceRef(
                        (String) doc.getMetadata().get(DocumentMetadata.TITLE),
                        (String) doc.getMetadata().get(DocumentMetadata.URL)))
                .distinct()
                .toList();

        return new QueryResponse(answer, sources);
    }
}
