package dev.saseq.services;

import dev.saseq.mcp.McpSamplingClient;
import dev.saseq.mcp.McpSamplingException;
import dev.saseq.mcp.SamplingRequest;
import dev.saseq.mcp.SamplingResponse;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Nested
    class PostResponseTests {

        @Mock
        private MessageReceivedEvent event;

        @Mock
        private MessageChannelUnion channel;

        @Mock
        private Message originalMessage;

        @Mock
        private MessageCreateAction messageAction;

        @Mock
        private Message sentMessage;

        private void setupPostMocks() {
            when(event.getChannel()).thenReturn(channel);
            when(event.getMessage()).thenReturn(originalMessage);
            when(channel.sendMessage(anyString())).thenReturn(messageAction);
            when(messageAction.setMessageReference(any(Message.class))).thenReturn(messageAction);
            when(messageAction.complete()).thenReturn(sentMessage);
        }

        @Test
        void postsResponseToCorrectChannel() {
            // Given: a sampling response
            setupPostMocks();
            SamplingResponse response = createMockResponse("Hello, this is Claude!");

            // When: the service posts the response
            service.postResponse(event, response);

            // Then: the response is sent to the event's channel
            verify(channel).sendMessage("Hello, this is Claude!");
        }

        @Test
        void repliesToOriginalMessage() {
            // Given: a sampling response
            setupPostMocks();
            SamplingResponse response = createMockResponse("This is a reply.");

            // When: the service posts the response
            service.postResponse(event, response);

            // Then: the message references the original
            verify(messageAction).setMessageReference(originalMessage);
        }

        @Test
        void splitsLongMessagesAt2000Chars() {
            // Given: a response longer than 2000 characters
            setupPostMocks();
            String longContent = "A".repeat(2500);
            SamplingResponse response = createMockResponse(longContent);

            // When: the service posts the response
            service.postResponse(event, response);

            // Then: the message is split into multiple parts
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(channel, times(2)).sendMessage(messageCaptor.capture());

            List<String> messages = messageCaptor.getAllValues();
            assertEquals(2, messages.size());
            assertTrue(messages.get(0).length() <= 2000);
            assertTrue(messages.get(1).length() <= 2000);
        }

        @Test
        void splitsAtWordBoundaryWhenPossible() {
            // Given: a response with words that needs splitting
            setupPostMocks();
            String content = "word ".repeat(450); // ~2250 chars
            SamplingResponse response = createMockResponse(content.trim());

            // When: the service posts the response
            service.postResponse(event, response);

            // Then: split happens at word boundary
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(channel, atLeast(2)).sendMessage(messageCaptor.capture());

            // First message shouldn't end mid-word
            String firstMessage = messageCaptor.getAllValues().get(0);
            assertTrue(firstMessage.endsWith("word") || firstMessage.endsWith(" "),
                    "Message should split at word boundary");
        }

        @Test
        void preservesMarkdownFormatting() {
            // Given: a response with markdown
            setupPostMocks();
            String markdownContent = "**Bold** and *italic* and `code`";
            SamplingResponse response = createMockResponse(markdownContent);

            // When: the service posts the response
            service.postResponse(event, response);

            // Then: markdown is preserved
            verify(channel).sendMessage(markdownContent);
        }

        @Test
        void handlesEmptyResponse() {
            // Given: an empty response
            setupPostMocks();
            SamplingResponse response = createMockResponse("");

            // When: the service posts the response
            service.postResponse(event, response);

            // Then: a fallback message is sent
            verify(channel).sendMessage("(No response generated)");
        }

        @Test
        void handlesNullContent() {
            // Given: a response with null content
            setupPostMocks();
            SamplingResponse response = new SamplingResponse();
            response.setContent(null);

            // When: the service posts the response
            service.postResponse(event, response);

            // Then: a fallback message is sent
            verify(channel).sendMessage("(No response generated)");
        }

        @Test
        void onlyFirstMessageRepliesForMultiPartResponse() {
            // Given: a response that will be split
            setupPostMocks();
            String longContent = "A".repeat(4500); // Will be split into 3 messages
            SamplingResponse response = createMockResponse(longContent);

            // When: the service posts the response
            service.postResponse(event, response);

            // Then: only the first message uses setMessageReference
            verify(messageAction, times(1)).setMessageReference(originalMessage);
        }
    }
}
