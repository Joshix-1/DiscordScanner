import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.user.User;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.event.ListenerManager;
import org.javacord.api.util.logging.ExceptionLogger;

import java.beans.ExceptionListener;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;

public class DiscordScanner {
    public static final long ALL_USERS = -1L, NO_BOTS = -2L;
    private static final Pattern NEW_LINE = Pattern.compile("(\n|&&)+");
    private static final char QUOTE_CHAR = '"';
    private final long user;
    private final Map<Long, Message> messages = new HashMap<>();
    ListenerManager<MessageCreateListener> messageCreateListenerListenerManager;
    private long lastHandled;
    private boolean terminated = false;
    private String[] currentMessage = null;
    private int currentIndex = 0;

    public DiscordScanner (Message after, MessageAuthor authorToHandle) {
        this(after, authorToHandle == null ? ALL_USERS : authorToHandle.getId());
    }

    public DiscordScanner (Message after, Long authorToHandle) {
        this.user = authorToHandle == null ? ALL_USERS : authorToHandle;
        this.lastHandled = after.getId();
        after.getChannel().getMessagesAfter(10, lastHandled).thenAccept(messages -> messages.forEach(m -> {
            if (authorIsOk(m.getAuthor())) {
                this.messages.put(m.getId(), m);
            }
        }));

        this.messageCreateListenerListenerManager = after.getChannel().addMessageCreateListener(event -> {
           if (authorIsOk(event.getMessageAuthor())) {
               this.messages.put(event.getMessageId(), event.getMessage());
           }
        });
    }

    private boolean authorIsOk(MessageAuthor author) {
        return user == ALL_USERS || (user == NO_BOTS && author.isRegularUser()) || author.getId() == user;
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
        if (this.currentMessage != null && this.currentMessage.length > this.currentIndex) {
            return true;
        }

        removeHandledMessages();
        return messages.size() != 0;
    }

    private boolean isEscaped(char[] chars, int i) {
        if (i == 0) {
            return false;
        }

        int countEscapes = 0;
        for (int j = i - 1; j >= 0; j--) {
            if (chars[j] == '\\') {
                countEscapes++;
            } else {
                break;
            }
        }

        return countEscapes % 2 == 1;
    }

    private String[] splitMessage(String message) {
        ArrayList<Integer> doubleQuotes = new ArrayList<>();

        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '"' && !isEscaped(chars, i)) {
                doubleQuotes.add(i);
            }
        }

        if (doubleQuotes.size() < 2) {
            return NEW_LINE.split(message);
        }

        if (doubleQuotes.size() % 2 == 1) { //if is odd, ignore last
            doubleQuotes.remove(doubleQuotes.size() - 1);
        }

        List<String> parts = new ArrayList<>();
        Matcher m = NEW_LINE.matcher(message);

        int lastEnd = 0;
        int quotesIndex = 0;
        while (m.find()) {
            int start = m.start();
            int end = m.end();

            if (doubleQuotes.get(quotesIndex + 1) < start) {
                if (quotesIndex + 2 < doubleQuotes.size()) {
                    quotesIndex += 2;
                }
            }
            if (!(start >= doubleQuotes.get(quotesIndex) && end <= doubleQuotes.get(quotesIndex + 1))) {
                parts.add(message.substring(lastEnd, start));
                lastEnd = end;
            }
        }

        if (lastEnd < message.length()) {
            parts.add(message.substring(lastEnd));
        }

        System.out.println(message + " -> " + Arrays.toString(parts.toArray(new String[0])));
        return parts.toArray(new String[0]);
    }

    public String nextLine() {
        if (!nextIsReady() || isTerminated()) {
            return null;
        }

        if (this.currentMessage != null && this.currentMessage.length > this.currentIndex) {
            return fixQuotes(this.currentMessage[this.currentIndex++]);
        }

        return messages.keySet().stream().sorted().findFirst().map(id -> {
            this.lastHandled = id;
            this.currentMessage = splitMessage(messages.get(id).getContent());
            this.currentIndex = 0;
            return fixQuotes(this.currentMessage[this.currentIndex++]);
        }).orElse(null);
    }

    private String fixQuotes(String line) {
        line = line.replace("\\\"", "\"");
        if (line.startsWith("\"") && line.endsWith("\"") && !isEscaped(line.toCharArray(), line.length() - 1)) {
            return line.substring(1, line.length() - 1);
        }
        return line;
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
