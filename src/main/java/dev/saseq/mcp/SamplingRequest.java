package dev.saseq.mcp;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an MCP sampling/createMessage request.
 * Used to request an LLM response via the MCP sampling protocol.
 */
public class SamplingRequest {

    private List<SamplingMessage> messages;
    private ModelPreferences modelPreferences;
    private String systemPrompt;
    private int maxTokens;

    public SamplingRequest() {
        this.messages = new ArrayList<>();
        this.maxTokens = 1024;
    }

    /**
     * Creates a SamplingRequest with Discord context included in the system prompt.
     *
     * @param userMessage the user's message content
     * @param channelName the Discord channel name
     * @param serverName the Discord server name
     * @param authorName the message author's name
     * @return a configured SamplingRequest
     */
    public static SamplingRequest withDiscordContext(String userMessage, String channelName,
                                                      String serverName, String authorName) {
        SamplingRequest request = new SamplingRequest();

        // Add the user message
        request.addMessage(new SamplingMessage("user", userMessage));

        // Build system prompt with Discord context
        String systemPrompt = String.format(
            "You are responding to a Discord message. Context:\n" +
            "- Server: %s\n" +
            "- Channel: %s\n" +
            "- Author: %s\n\n" +
            "Respond helpfully and appropriately for a Discord conversation.",
            serverName, channelName, authorName
        );
        request.setSystemPrompt(systemPrompt);

        return request;
    }

    public List<SamplingMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<SamplingMessage> messages) {
        this.messages = messages;
    }

    public void addMessage(SamplingMessage message) {
        this.messages.add(message);
    }

    public ModelPreferences getModelPreferences() {
        return modelPreferences;
    }

    public void setModelPreferences(ModelPreferences modelPreferences) {
        this.modelPreferences = modelPreferences;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * Represents model preferences for the sampling request.
     */
    public static class ModelPreferences {
        private List<String> hints;
        private Double costPriority;
        private Double speedPriority;
        private Double intelligencePriority;

        public ModelPreferences() {
            this.hints = new ArrayList<>();
        }

        public List<String> getHints() {
            return hints;
        }

        public void setHints(List<String> hints) {
            this.hints = hints;
        }

        public void addHint(String hint) {
            this.hints.add(hint);
        }

        public Double getCostPriority() {
            return costPriority;
        }

        public void setCostPriority(Double costPriority) {
            this.costPriority = costPriority;
        }

        public Double getSpeedPriority() {
            return speedPriority;
        }

        public void setSpeedPriority(Double speedPriority) {
            this.speedPriority = speedPriority;
        }

        public Double getIntelligencePriority() {
            return intelligencePriority;
        }

        public void setIntelligencePriority(Double intelligencePriority) {
            this.intelligencePriority = intelligencePriority;
        }
    }
}
