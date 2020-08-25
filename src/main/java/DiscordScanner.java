import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.user.User;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DiscordScanner {
    public static final long ALL_USERS = -1L;
    private final long user;
    private final Map<Long, Message> messages = new HashMap<>();
    ListenerManager<MessageCreateListener> messageCreateListenerListenerManager;
    private long lastHandled;
    private boolean terminated = false;

    public DiscordScanner (Message after, MessageAuthor authorToHandle) {
        this(after, authorToHandle == null ? ALL_USERS : authorToHandle.getId());
    }

    public DiscordScanner (Message after, Long authorToHandle) {
        this.user = authorToHandle == null ? ALL_USERS : authorToHandle;
        this.lastHandled = after.getId();
        after.getChannel().getMessagesAfter(10, lastHandled).thenAccept(messages -> messages.forEach(m -> {
            if (user == ALL_USERS || m.getAuthor().getId() == user) {
                this.messages.put(m.getId(), m);
            }
        }));

        this.messageCreateListenerListenerManager = after.getChannel().addMessageCreateListener(event -> {
           if (user == ALL_USERS || event.getMessageAuthor().getId() == user) {
               this.messages.put(event.getMessageId(), event.getMessage());
           }
        });
    }

    private void removeHandledMessages() {
        if (messages.isEmpty()) {
            return;
        }

        messages.keySet().iterator().forEachRemaining(id -> {
            if (id <= this.lastHandled) { //message is older or is last and should've been handled.
                messages.remove(id);
            }
        });
    }

    public boolean nextIsReady() {
        removeHandledMessages();
        return messages.size() != 0;
    }

    public String nextString() {
        Message m = nextMessage();

        return m == null ? null : m.getContent();
    }

    public Message nextMessage() {
        if (!nextIsReady() || isTerminated()) {
            return null;
        }

        return messages.keySet().stream().sorted().findFirst().map(id -> {
            this.lastHandled = id;
            return messages.get(id);
        }).orElse(null);
    }

    public void terminate() {
        messageCreateListenerListenerManager.remove();
        messages.clear();
        lastHandled = 0;
        terminated = true;
    }

    public boolean isTerminated() {
        return terminated;
    }
}
