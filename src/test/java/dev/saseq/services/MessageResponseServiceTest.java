package dev.saseq.services;

import dev.saseq.mcp.McpSamplingClient;
import dev.saseq.mcp.McpSamplingException;
import dev.saseq.mcp.SamplingRequest;
import dev.saseq.mcp.SamplingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageResponseServiceTest {

    @Mock
    private McpSamplingClient samplingClient;

    private MessageResponseService service;

    @BeforeEach
    void setUp() {
        service = new MessageResponseService(samplingClient);
    }

    @Test
    void serviceInvokesSamplingClientWithCorrectContext() throws Exception {
        // Given
        SamplingResponse mockResponse = createMockResponse("Hello!");
        when(samplingClient.sendSamplingRequest(any())).thenReturn(mockResponse);

        // When
        CompletableFuture<SamplingResponse> future = service.processMessage(
                "Hello bot!",
                "general",
                "Test Server",
                "TestUser"
        );
        SamplingResponse response = future.get(5, TimeUnit.SECONDS);

        // Then
        ArgumentCaptor<SamplingRequest> captor = ArgumentCaptor.forClass(SamplingRequest.class);
        verify(samplingClient).sendSamplingRequest(captor.capture());

        SamplingRequest sentRequest = captor.getValue();
        assertNotNull(sentRequest.getSystemPrompt());
        assertTrue(sentRequest.getSystemPrompt().contains("Test Server"));
        assertTrue(sentRequest.getSystemPrompt().contains("general"));
        assertTrue(sentRequest.getSystemPrompt().contains("TestUser"));
        assertEquals(1, sentRequest.getMessages().size());
        assertEquals("user", sentRequest.getMessages().get(0).getRole());
        assertEquals("Hello bot!", sentRequest.getMessages().get(0).getContent().getText());

        assertEquals("Hello!", response.getContent().getText());
    }

    @Test
    void serviceHandlesSamplingClientException() throws Exception {
        // Given
        when(samplingClient.sendSamplingRequest(any()))
                .thenThrow(new McpSamplingException("Connection failed"));

        // When
        CompletableFuture<SamplingResponse> future = service.processMessage(
                "Hello",
                "general",
                "Test Server",
                "TestUser"
        );

        // Then
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof McpSamplingException);
        assertEquals("Connection failed", exception.getCause().getMessage());
    }

    @Test
    void processingIsAsync() throws Exception {
        // Given - a slow sampling client
        when(samplingClient.sendSamplingRequest(any())).thenAnswer(invocation -> {
            Thread.sleep(100); // Simulate slow response
            return createMockResponse("Delayed response");
        });

        // When - start processing
        long startTime = System.currentTimeMillis();
        CompletableFuture<SamplingResponse> future = service.processMessage(
                "Hello",
                "general",
                "Test Server",
                "TestUser"
        );
        long elapsedBeforeGet = System.currentTimeMillis() - startTime;

        // Then - the call should return immediately (async)
        assertTrue(elapsedBeforeGet < 50, "processMessage should return immediately, not block");
        assertFalse(future.isDone(), "Future should not be complete immediately");

        // And the result should eventually be available
        SamplingResponse response = future.get(5, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals("Delayed response", response.getContent().getText());
    }

    @Test
    void serviceHandlesDmContext() throws Exception {
        // Given
        SamplingResponse mockResponse = createMockResponse("DM response");
        when(samplingClient.sendSamplingRequest(any())).thenReturn(mockResponse);

        // When - process a DM (no server name)
        CompletableFuture<SamplingResponse> future = service.processMessage(
                "Hello in DM",
                null,
                null,
                "TestUser"
        );
        future.get(5, TimeUnit.SECONDS);

        // Then
        ArgumentCaptor<SamplingRequest> captor = ArgumentCaptor.forClass(SamplingRequest.class);
        verify(samplingClient).sendSamplingRequest(captor.capture());

        SamplingRequest sentRequest = captor.getValue();
        assertNotNull(sentRequest.getSystemPrompt());
        assertTrue(sentRequest.getSystemPrompt().contains("Direct Message"));
        assertTrue(sentRequest.getSystemPrompt().contains("TestUser"));
    }

    private SamplingResponse createMockResponse(String text) {
        SamplingResponse response = new SamplingResponse();
        SamplingResponse.SamplingContent content = new SamplingResponse.SamplingContent("text", text);
        response.setContent(content);
        response.setModel("test-model");
        response.setStopReason("end_turn");
        return response;
    }
}
