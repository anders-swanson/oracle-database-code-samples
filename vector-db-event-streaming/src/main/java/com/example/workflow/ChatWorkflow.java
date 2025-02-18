package com.example.workflow;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.example.chat.OCIChatService;
import com.example.embeddingmodel.OCIEmbeddingService;
import com.example.model.Embedding;
import com.example.vectorstore.OracleVectorStore;
import com.example.vectorstore.SearchRequest;
import lombok.Builder;

@Builder
public class ChatWorkflow implements Callable<String> {
    private final OracleVectorStore vectorStore;
    private final OCIChatService chatService;
    private final OCIEmbeddingService embeddingModel;

    /**
     * The raw user question.
     */
    private final String userQuestion;

    /**
     * The prompt template we'll be using for RAG.
     */
    private final String promptTemplate;

    /**
     * The minimum similarity score allowed for result filtering,
     * where 1.0 is the most restrictive,
     * and 0.0 is the most permissive.
     */
    private final double minScore;

    @Override
    public String call() throws Exception {
        // Embed the user question to a vector embedding.
        Embedding embedding = embeddingModel.embed(userQuestion);
        // Create a search request using the user question's embedding.
        SearchRequest searchRequest = SearchRequest.builder()
                .text(embedding.content())
                .vector(embedding.vector())
                .maxResults(5)
                .minScore(minScore)
                .build();
        // Query the vector store for content related to the user question.
        List<Embedding> results = vectorStore.search(searchRequest);
        // If related content was found, add it to the chat service prompt.
        String context = results.isEmpty() ?
                // If there is no relevant entries, do not try to make something up.
                "Reply that no context was found in the vector database, and that you do not know the answer." :
                results.stream()
                        .map(Embedding::content)
                        .collect(Collectors.joining(", "));;
        String prompt = String.format(promptTemplate, userQuestion, context);
        // Call the chat service using the content-enriched prompt.
        return chatService.chat(prompt);
    }
}
