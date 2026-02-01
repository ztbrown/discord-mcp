package dev.saseq.services;

import dev.saseq.mcp.McpSamplingClient;
import dev.saseq.mcp.SamplingMessage;
import dev.saseq.mcp.SamplingRequest;
import dev.saseq.mcp.SamplingResponse;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
    private static final int DISCORD_MESSAGE_LIMIT = 2000;
    private static final String FALLBACK_MESSAGE = "(No response generated)";

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

    /**
     * Post a sampling response back to Discord.
     * Handles message splitting for responses longer than Discord's 2000 character limit.
     * The first message will reply to the original message for context.
     *
     * @param event the original message event to reply to
     * @param response the sampling response from Claude
     */
    public void postResponse(MessageReceivedEvent event, SamplingResponse response) {
        String content = extractContent(response);
        MessageChannelUnion channel = event.getChannel();
        Message originalMessage = event.getMessage();

        List<String> messageParts = splitMessage(content);
        boolean isFirstMessage = true;

        for (String part : messageParts) {
            if (isFirstMessage) {
                // First message replies to the original for context
                channel.sendMessage(part)
                        .setMessageReference(originalMessage)
                        .complete();
                isFirstMessage = false;
            } else {
                // Subsequent messages are sent without reply reference
                channel.sendMessage(part).complete();
            }
        }

        logger.debug("Posted {} message(s) in response to message from {}",
                messageParts.size(), event.getAuthor().getName());
    }

    /**
     * Extract the text content from a sampling response.
     * Returns a fallback message if content is null or empty.
     */
    private String extractContent(SamplingResponse response) {
        if (response == null || response.getContent() == null) {
            return FALLBACK_MESSAGE;
        }

        String text = response.getContent().getText();
        if (text == null || text.isEmpty()) {
            return FALLBACK_MESSAGE;
        }

        return text;
    }

    /**
     * Split a message into parts that fit within Discord's character limit.
     * Attempts to split at word boundaries when possible.
     *
     * @param content the full message content
     * @return a list of message parts, each within the Discord limit
     */
    List<String> splitMessage(String content) {
        List<String> parts = new ArrayList<>();

        if (content.length() <= DISCORD_MESSAGE_LIMIT) {
            parts.add(content);
            return parts;
        }

        int startIndex = 0;
        while (startIndex < content.length()) {
            int endIndex = Math.min(startIndex + DISCORD_MESSAGE_LIMIT, content.length());

            // If we're not at the end of the content, try to find a word boundary
            if (endIndex < content.length()) {
                int lastSpace = content.lastIndexOf(' ', endIndex);
                // Only use the space if it's reasonably close (within 200 chars of the limit)
                if (lastSpace > startIndex && lastSpace > endIndex - 200) {
                    endIndex = lastSpace;
                }
            }

            parts.add(content.substring(startIndex, endIndex).trim());
            startIndex = endIndex;

            // Skip leading space in next part
            while (startIndex < content.length() && content.charAt(startIndex) == ' ') {
                startIndex++;
            }
        }

        return parts;
    }
}
