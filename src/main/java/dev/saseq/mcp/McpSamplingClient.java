package dev.saseq.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP Sampling Client that sends JSON-RPC requests and receives responses.
 * Thread-safe for concurrent requests with configurable timeout.
 */
public class McpSamplingClient {

    private final PrintStream outputStream;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter;
    private final Object writeLock;
    private final Object readLock;
    private final BufferedReader reader;

    private Duration timeout;

    public McpSamplingClient(InputStream inputStream, PrintStream outputStream) {
        this.outputStream = outputStream;
        this.objectMapper = new ObjectMapper();
        this.requestIdCounter = new AtomicLong(0);
        this.writeLock = new Object();
        this.readLock = new Object();
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
        this.timeout = Duration.ofSeconds(30);
    }

    /**
     * Sends a sampling request and waits for the response.
     *
     * @param request the sampling request to send
     * @return the sampling response
     * @throws McpSamplingException if the request fails or times out
     */
    public SamplingResponse sendSamplingRequest(SamplingRequest request) throws McpSamplingException {
        long requestId = requestIdCounter.incrementAndGet();

        // Build JSON-RPC request
        ObjectNode jsonRpcRequest = objectMapper.createObjectNode();
        jsonRpcRequest.put("jsonrpc", "2.0");
        jsonRpcRequest.put("method", "sampling/createMessage");
        jsonRpcRequest.put("id", requestId);

        ObjectNode params = objectMapper.createObjectNode();
        params.set("messages", objectMapper.valueToTree(request.getMessages()));
        if (request.getSystemPrompt() != null) {
            params.put("systemPrompt", request.getSystemPrompt());
        }
        params.put("maxTokens", request.getMaxTokens());
        if (request.getModelPreferences() != null) {
            params.set("modelPreferences", objectMapper.valueToTree(request.getModelPreferences()));
        }
        jsonRpcRequest.set("params", params);

        // Send request (thread-safe)
        try {
            String jsonString = objectMapper.writeValueAsString(jsonRpcRequest);
            synchronized (writeLock) {
                outputStream.println(jsonString);
                outputStream.flush();
            }
        } catch (Exception e) {
            throw new McpSamplingException("Failed to send request", e);
        }

        // Read response with timeout (thread-safe)
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> {
                synchronized (readLock) {
                    return reader.readLine();
                }
            });

            String responseLine = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (responseLine == null) {
                throw new McpSamplingException("No response received");
            }

            return parseResponse(responseLine);
        } catch (TimeoutException e) {
            throw new McpSamplingException("Request timed out after " + timeout.toSeconds() + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpSamplingException("Request interrupted", e);
        } catch (ExecutionException e) {
            throw new McpSamplingException("Failed to read response", e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private SamplingResponse parseResponse(String responseLine) throws McpSamplingException {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseLine);

            // Check for error
            if (jsonNode.has("error")) {
                JsonNode error = jsonNode.get("error");
                int code = error.get("code").asInt();
                String message = error.get("message").asText();
                throw new McpSamplingException(message, code);
            }

            // Parse result
            JsonNode result = jsonNode.get("result");
            if (result == null) {
                throw new McpSamplingException("Missing result in response");
            }

            return objectMapper.treeToValue(result, SamplingResponse.class);
        } catch (McpSamplingException e) {
            throw e;
        } catch (Exception e) {
            throw new McpSamplingException("Failed to parse response", e);
        }
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
