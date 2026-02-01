package dev.saseq.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class McpSamplingClientTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void clientSendsProperlyFormattedJsonRpc() throws Exception {
        // Given
        PipedInputStream clientInput = new PipedInputStream();
        PipedOutputStream testOutput = new PipedOutputStream(clientInput);
        ByteArrayOutputStream clientOutput = new ByteArrayOutputStream();

        McpSamplingClient client = new McpSamplingClient(clientInput, new PrintStream(clientOutput));
        SamplingRequest request = new SamplingRequest();
        request.setSystemPrompt("Test prompt");
        request.addMessage(new SamplingMessage("user", "Hello"));

        // Provide a mock response so the client doesn't block
        String mockResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":{\"type\":\"text\",\"text\":\"Hi there\"},\"model\":\"test-model\",\"stopReason\":\"end_turn\"}}\n";
        testOutput.write(mockResponse.getBytes());
        testOutput.flush();

        // When
        client.sendSamplingRequest(request);

        // Then
        String sentJson = clientOutput.toString();
        assertNotNull(sentJson);
        assertTrue(sentJson.endsWith("\n"), "JSON-RPC message should end with newline");

        JsonNode jsonNode = objectMapper.readTree(sentJson.trim());
        assertEquals("2.0", jsonNode.get("jsonrpc").asText());
        assertEquals("sampling/createMessage", jsonNode.get("method").asText());
        assertNotNull(jsonNode.get("id"));
        assertNotNull(jsonNode.get("params"));

        JsonNode params = jsonNode.get("params");
        assertTrue(params.has("messages"));
        assertTrue(params.has("systemPrompt"));
        assertTrue(params.has("maxTokens"));
    }

    @Test
    void clientParsesSuccessfulResponse() throws Exception {
        // Given
        String successResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":{\"type\":\"text\",\"text\":\"Hello from Claude!\"},\"model\":\"claude-3-sonnet\",\"stopReason\":\"end_turn\"}}\n";
        InputStream inputStream = new ByteArrayInputStream(successResponse.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        McpSamplingClient client = new McpSamplingClient(inputStream, new PrintStream(outputStream));
        SamplingRequest request = new SamplingRequest();
        request.addMessage(new SamplingMessage("user", "Hello"));

        // When
        SamplingResponse response = client.sendSamplingRequest(request);

        // Then
        assertNotNull(response);
        assertEquals("Hello from Claude!", response.getContent().getText());
        assertEquals("text", response.getContent().getType());
        assertEquals("claude-3-sonnet", response.getModel());
        assertEquals("end_turn", response.getStopReason());
    }

    @Test
    void clientHandlesTimeout() {
        // Given - an input stream that never returns data
        InputStream slowInputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    Thread.sleep(10000); // Sleep longer than timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return -1;
            }
        };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        McpSamplingClient client = new McpSamplingClient(slowInputStream, new PrintStream(outputStream));
        client.setTimeout(Duration.ofMillis(100)); // Short timeout for test

        SamplingRequest request = new SamplingRequest();
        request.addMessage(new SamplingMessage("user", "Hello"));

        // When/Then
        assertThrows(McpSamplingException.class, () -> client.sendSamplingRequest(request));
    }

    @Test
    void clientHandlesErrorResponse() throws Exception {
        // Given
        String errorResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32600,\"message\":\"Invalid Request\"}}\n";
        InputStream inputStream = new ByteArrayInputStream(errorResponse.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        McpSamplingClient client = new McpSamplingClient(inputStream, new PrintStream(outputStream));
        SamplingRequest request = new SamplingRequest();
        request.addMessage(new SamplingMessage("user", "Hello"));

        // When/Then
        McpSamplingException exception = assertThrows(McpSamplingException.class,
            () -> client.sendSamplingRequest(request));
        assertTrue(exception.getMessage().contains("Invalid Request"));
        assertEquals(-32600, exception.getErrorCode());
    }

    @Test
    void defaultTimeoutIs30Seconds() {
        // Given
        McpSamplingClient client = new McpSamplingClient(
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(new ByteArrayOutputStream())
        );

        // Then
        assertEquals(Duration.ofSeconds(30), client.getTimeout());
    }

    @Test
    void timeoutIsConfigurable() {
        // Given
        McpSamplingClient client = new McpSamplingClient(
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(new ByteArrayOutputStream())
        );

        // When
        client.setTimeout(Duration.ofSeconds(60));

        // Then
        assertEquals(Duration.ofSeconds(60), client.getTimeout());
    }

    @Test
    void clientIsThreadSafeForConcurrentRequests() throws Exception {
        // Given
        // Create a mock input that provides responses for multiple requests
        String responses =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":{\"type\":\"text\",\"text\":\"Response 1\"},\"model\":\"test\",\"stopReason\":\"end_turn\"}}\n" +
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":{\"type\":\"text\",\"text\":\"Response 2\"},\"model\":\"test\",\"stopReason\":\"end_turn\"}}\n";
        InputStream inputStream = new ByteArrayInputStream(responses.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        McpSamplingClient client = new McpSamplingClient(inputStream, new PrintStream(outputStream));

        SamplingRequest request1 = new SamplingRequest();
        request1.addMessage(new SamplingMessage("user", "Request 1"));

        SamplingRequest request2 = new SamplingRequest();
        request2.addMessage(new SamplingMessage("user", "Request 2"));

        // When - send requests concurrently
        CompletableFuture<SamplingResponse> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                return client.sendSamplingRequest(request1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<SamplingResponse> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                return client.sendSamplingRequest(request2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then - both should complete successfully (order may vary)
        SamplingResponse response1 = future1.get();
        SamplingResponse response2 = future2.get();

        assertNotNull(response1);
        assertNotNull(response2);
        assertTrue(response1.getContent().getText().startsWith("Response"));
        assertTrue(response2.getContent().getText().startsWith("Response"));
    }
}
