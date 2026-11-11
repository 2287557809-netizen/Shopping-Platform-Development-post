package hu.unimiskolc.shopstream.notification;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The course asks notification-service to log events; a database is explicitly
 * not required. A bounded in-memory store keeps the demo observable through
 * GET /api/notifications without adding another data store.
 */
@Component
public class NotificationStore {

    private static final int MAX_NOTIFICATIONS = 200;
    private static final int MAX_SEEN_EVENT_IDS = 5000;

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentLinkedDeque<Notification> notifications = new ConcurrentLinkedDeque<>();
    private final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();

    public boolean addIfNew(String eventId, String eventType, Long orderId, String message) {
        if (eventId != null) {
            if (seenEventIds.size() > MAX_SEEN_EVENT_IDS) {
                seenEventIds.clear();
            }
            if (!seenEventIds.add(eventId)) {
                return false;
            }
        }

        notifications.addFirst(new Notification(
                sequence.incrementAndGet(), eventType, orderId, message, Instant.now()));

        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.removeLast();
        }
        return true;
    }

    public List<Notification> latest(int limit) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        return notifications.stream().limit(safeLimit).toList();
    }

    public List<Notification> byOrder(Long orderId) {
        return notifications.stream()
                .filter(notification -> orderId.equals(notification.orderId()))
                .toList();
    }
}
