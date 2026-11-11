package hu.unimiskolc.shopstream.order;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventPublisher {

    private final EventPublisher publisher;

    public OrderEventPublisher(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        OrderCreatedPayload payload = new OrderCreatedPayload(
                event.orderId(), event.items(), event.totalPrice());
        publisher.publish("orderEvents-out-0", String.valueOf(event.orderId()), "OrderCreated", payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        String eventType = event.status() == OrderStatus.CONFIRMED ? "OrderConfirmed" : "OrderRejected";
        publisher.publish("orderEvents-out-0", String.valueOf(event.orderId()), eventType, event);
    }
}
