package dev.saseq.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscordMessageListenerTest {

    private static final String BOT_USER_ID = "123456789";

    @Mock
    private MessageReceivedEvent event;

    @Mock
    private Message message;

    @Mock
    private User author;

    @Mock
    private SelfUser selfUser;

    @Mock
    private User mentionedBot;

    @Mock
    private Mentions mentions;

    @Mock
    private JDA jda;

    private DiscordMessageListener listener;
    private boolean messageProcessed;

    @BeforeEach
    void setUp() {
        messageProcessed = false;
        listener = new DiscordMessageListener(true) {
            @Override
            protected void processMessage(MessageReceivedEvent event) {
                messageProcessed = true;
            }
        };
    }

    private void setupCommonMocks() {
        when(event.getAuthor()).thenReturn(author);
        when(event.getMessage()).thenReturn(message);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getSelfUser()).thenReturn(selfUser);
        when(selfUser.getId()).thenReturn(BOT_USER_ID);
        when(author.isBot()).thenReturn(false);
    }

    @Test
    void respondsToMention() {
        // Given: a message that @mentions the bot in a guild channel
        setupCommonMocks();
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);
        when(mentionedBot.getId()).thenReturn(BOT_USER_ID);
        when(message.getMentions()).thenReturn(mentions);
        when(mentions.getUsers()).thenReturn(List.of(mentionedBot));

        // When: the listener receives the message
        listener.onMessageReceived(event);

        // Then: the message should be processed
        assertTrue(messageProcessed, "Message mentioning bot should be processed");
    }

    @Test
    void respondsToDm() {
        // Given: a DM to the bot
        setupCommonMocks();
        when(event.getChannelType()).thenReturn(ChannelType.PRIVATE);
        when(message.getMentions()).thenReturn(mentions);
        when(mentions.getUsers()).thenReturn(List.of());

        // When: the listener receives the DM
        listener.onMessageReceived(event);

        // Then: the message should be processed
        assertTrue(messageProcessed, "DM should be processed");
    }

    @Test
    void ignoresChannelMessageWithoutMention() {
        // Given: a message in a guild channel that doesn't mention the bot
        setupCommonMocks();
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);
        when(message.getMentions()).thenReturn(mentions);
        when(mentions.getUsers()).thenReturn(List.of());

        // When: the listener receives the message
        listener.onMessageReceived(event);

        // Then: the message should NOT be processed
        assertFalse(messageProcessed, "Channel message without mention should be ignored");
    }

    @Test
    void configDisablesMentionFilter() {
        // Given: mention filter is disabled via config
        listener = new DiscordMessageListener(false) {
            @Override
            protected void processMessage(MessageReceivedEvent event) {
                messageProcessed = true;
            }
        };
        // Only set up minimal mocks needed for this test (no JDA/selfUser needed)
        when(event.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(false);
        when(event.getChannelType()).thenReturn(ChannelType.TEXT);

        // When: the listener receives any message
        listener.onMessageReceived(event);

        // Then: the message should be processed (no mention required)
        assertTrue(messageProcessed, "Message should be processed when mention filter is disabled");
    }

    @Test
    void ignoresBotMessages() {
        // Given: a message from a bot
        when(event.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(true);

        // When: the listener receives the message
        listener.onMessageReceived(event);

        // Then: the message should NOT be processed
        assertFalse(messageProcessed, "Bot messages should be ignored");
    }
}
