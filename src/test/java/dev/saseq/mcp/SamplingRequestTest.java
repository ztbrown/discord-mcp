package dev.saseq.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SamplingRequestTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void samplingRequestSerializesToValidJson() throws Exception {
        // Given
        SamplingRequest request = new SamplingRequest();
        request.setSystemPrompt("You are a helpful assistant.");
        request.setMaxTokens(1024);
        request.addMessage(new SamplingMessage("user", "Hello, world!"));

        // When
        String json = objectMapper.writeValueAsString(request);

        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"messages\""));
        assertTrue(json.contains("\"systemPrompt\""));
        assertTrue(json.contains("\"maxTokens\""));

        // Verify it can be deserialized back
        SamplingRequest deserialized = objectMapper.readValue(json, SamplingRequest.class);
        assertEquals(request.getMaxTokens(), deserialized.getMaxTokens());
        assertEquals(request.getSystemPrompt(), deserialized.getSystemPrompt());
    }

    @Test
    void messagesArrayContainsUserMessage() {
        // Given
        SamplingRequest request = new SamplingRequest();
        String userContent = "What is the weather?";

        // When
        request.addMessage(new SamplingMessage("user", userContent));

        // Then
        assertEquals(1, request.getMessages().size());
        SamplingMessage message = request.getMessages().get(0);
        assertEquals("user", message.getRole());
        assertEquals("text", message.getContent().getType());
        assertEquals(userContent, message.getContent().getText());
    }

    @Test
    void systemPromptIncludesDiscordContext() {
        // Given
        String channelName = "general";
        String serverName = "My Server";
        String authorName = "TestUser";
        String userMessage = "Hello!";

        // When
        SamplingRequest request = SamplingRequest.withDiscordContext(
            userMessage, channelName, serverName, authorName
        );

        // Then
        String systemPrompt = request.getSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains(channelName), "System prompt should contain channel name");
        assertTrue(systemPrompt.contains(serverName), "System prompt should contain server name");
        assertTrue(systemPrompt.contains(authorName), "System prompt should contain author name");
    }

    @Test
    void defaultMaxTokensIs1024() {
        SamplingRequest request = new SamplingRequest();
        assertEquals(1024, request.getMaxTokens());
    }

    @Test
    void modelPreferencesCanBeSet() {
        // Given
        SamplingRequest request = new SamplingRequest();
        SamplingRequest.ModelPreferences prefs = new SamplingRequest.ModelPreferences();
        prefs.addHint("claude-3-sonnet");
        prefs.setSpeedPriority(0.8);
        prefs.setCostPriority(0.5);
        prefs.setIntelligencePriority(0.7);

        // When
        request.setModelPreferences(prefs);

        // Then
        assertNotNull(request.getModelPreferences());
        assertEquals(1, request.getModelPreferences().getHints().size());
        assertEquals("claude-3-sonnet", request.getModelPreferences().getHints().get(0));
        assertEquals(0.8, request.getModelPreferences().getSpeedPriority());
    }

    @Test
    void samplingMessageContentHasCorrectStructure() throws Exception {
        // Given
        SamplingMessage message = new SamplingMessage("user", "Test content");

        // When
        String json = objectMapper.writeValueAsString(message);

        // Then
        assertTrue(json.contains("\"role\":\"user\""));
        assertTrue(json.contains("\"content\""));
        assertTrue(json.contains("\"type\":\"text\""));
        assertTrue(json.contains("\"text\":\"Test content\""));
    }
}
