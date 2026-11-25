package hu.unimiskolc.shopstream.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes order events only after the database transaction commits. An order
 * that is rolled back is never announced, and the PENDING row is already
 * durable when product-service receives OrderPlaced.
 *
 * <p>The topic names live in config-server (app.topics.*), not in the code.</p>
 */
@Component
public class OrderEventPublisher {

    private final EventPublisher publisher;

    @Value("${app.topics.order-events:order-events}")
    private String orderEventsTopic;

    public OrderEventPublisher(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        OrderPlacedPayload payload = new OrderPlacedPayload(
                event.orderId(), event.items(), event.totalPrice());
        publisher.publish(orderEventsTopic, String.valueOf(event.orderId()), "OrderPlaced", payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        String eventType = event.status() == OrderStatus.CONFIRMED ? "OrderConfirmed" : "OrderRejected";
        publisher.publish(orderEventsTopic, String.valueOf(event.orderId()), eventType, event);
    }
}
