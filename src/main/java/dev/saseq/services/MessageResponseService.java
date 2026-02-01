package dev.saseq.services;

import dev.saseq.mcp.McpSamplingClient;
import dev.saseq.mcp.SamplingMessage;
import dev.saseq.mcp.SamplingRequest;
import dev.saseq.mcp.SamplingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for processing Discord messages through the MCP sampling client.
 * Handles async processing to avoid blocking the JDA event thread.
 */
@Service
public class MessageResponseService {

    private static final Logger logger = LoggerFactory.getLogger(MessageResponseService.class);

    private final McpSamplingClient samplingClient;
    private final ExecutorService executor;

    public MessageResponseService(McpSamplingClient samplingClient) {
        this.samplingClient = samplingClient;
        this.executor = Executors.newCachedThreadPool();
    }

    /**
     * Process a Discord message asynchronously through the MCP sampling client.
     *
     * @param messageContent the content of the Discord message
     * @param channelName the name of the channel (null for DMs)
     * @param serverName the name of the server (null for DMs)
     * @param authorName the name of the message author
     * @return a CompletableFuture containing the sampling response
     */
    public CompletableFuture<SamplingResponse> processMessage(
            String messageContent,
            String channelName,
            String serverName,
            String authorName) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                SamplingRequest request = buildRequest(messageContent, channelName, serverName, authorName);
                logger.debug("Sending sampling request for message from {}", authorName);
                return samplingClient.sendSamplingRequest(request);
            } catch (Exception e) {
                logger.error("Failed to process message from {}: {}", authorName, e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private SamplingRequest buildRequest(String messageContent, String channelName,
                                          String serverName, String authorName) {
        SamplingRequest request = new SamplingRequest();
        request.addMessage(new SamplingMessage("user", messageContent));

        String systemPrompt = buildSystemPrompt(channelName, serverName, authorName);
        request.setSystemPrompt(systemPrompt);

        return request;
    }

    private String buildSystemPrompt(String channelName, String serverName, String authorName) {
        if (serverName == null || channelName == null) {
            return String.format(
                "You are responding to a Discord Direct Message. Context:\n" +
                "- Direct Message from: %s\n\n" +
                "Respond helpfully and appropriately for a Discord conversation.",
                authorName
            );
        }

        return String.format(
            "You are responding to a Discord message. Context:\n" +
            "- Server: %s\n" +
            "- Channel: %s\n" +
            "- Author: %s\n\n" +
            "Respond helpfully and appropriately for a Discord conversation.",
            serverName, channelName, authorName
        );
    }
}
