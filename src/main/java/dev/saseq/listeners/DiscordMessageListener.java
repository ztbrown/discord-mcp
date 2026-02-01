package dev.saseq.listeners;

import dev.saseq.services.MessageResponseService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Listener for incoming Discord messages.
 * Filters to only process messages that @mention the bot or are DMs.
 * Wires to MessageResponseService for MCP sampling.
 */
@Component
public class DiscordMessageListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DiscordMessageListener.class);

    private final boolean respondToMentionsOnly;
    private final MessageResponseService messageResponseService;

    @Autowired
    public DiscordMessageListener(
            @Value("${DISCORD_RESPOND_TO_MENTIONS:true}") boolean respondToMentionsOnly,
            MessageResponseService messageResponseService) {
        this.respondToMentionsOnly = respondToMentionsOnly;
        this.messageResponseService = messageResponseService;
    }

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

        // If mention filter is enabled, check for mention or DM
        if (respondToMentionsOnly) {
            boolean isDm = channelType == ChannelType.PRIVATE;
            boolean isMentioned = isBotMentioned(event);

            if (!isDm && !isMentioned) {
                // Skip messages in channels where bot is not mentioned
                return;
            }
        }

        processMessage(event);
    }

    /**
     * Check if the bot is mentioned in the message.
     */
    private boolean isBotMentioned(MessageReceivedEvent event) {
        String botId = event.getJDA().getSelfUser().getId();
        Message message = event.getMessage();

        for (User mentionedUser : message.getMentions().getUsers()) {
            if (mentionedUser.getId().equals(botId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Process a message that passed all filters.
     * Sends the message to the MCP sampling client asynchronously.
     */
    protected void processMessage(MessageReceivedEvent event) {
        Message message = event.getMessage();
        String content = message.getContentDisplay();
        String authorName = event.getAuthor().getName();

        // Extract context from the event
        String channelName = event.getChannel().getName();
        String serverName = null;
        Guild guild = event.isFromGuild() ? event.getGuild() : null;
        if (guild != null) {
            serverName = guild.getName();
        }

        logger.debug("Processing message from {} in {}/{}: {}",
                authorName,
                serverName != null ? serverName : "DM",
                channelName,
                content);

        // Process asynchronously through the sampling client
        messageResponseService.processMessage(content, channelName, serverName, authorName)
                .thenAccept(response -> {
                    logger.debug("Received sampling response for message from {}: {}",
                            authorName,
                            response.getContent().getText());
                    // Response handling will be implemented in dm-2oe
                })
                .exceptionally(error -> {
                    logger.error("Failed to get sampling response for message from {}: {}",
                            authorName,
                            error.getMessage());
                    return null;
                });
    }
}
