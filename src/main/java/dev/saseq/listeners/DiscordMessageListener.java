package dev.saseq.listeners;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listener for incoming Discord messages.
 * Filters to only process text messages and ignores bot messages.
 */
@Component
public class DiscordMessageListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DiscordMessageListener.class);

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore messages from bots
        if (event.getAuthor().isBot()) {
            return;
        }

        // Only process text channels and private channels (DMs)
        ChannelType channelType = event.getChannelType();
        if (!channelType.isMessage()) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentDisplay();

        // Log the message for debugging
        logger.debug("Received message from {}: {}",
                event.getAuthor().getName(),
                content);
    }
}
