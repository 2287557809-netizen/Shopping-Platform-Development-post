package hu.unimiskolc.shopstream.notification;

import java.time.Instant;

public record Notification(
        long id,
        String eventType,
        Long orderId,
        String message,
        Instant createdAt) {
}
