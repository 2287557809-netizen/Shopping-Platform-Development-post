package hu.unimiskolc.shopstream.product;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Kafka is only contacted after the database transaction commits. This avoids
 * announcing products that were later rolled back.
 */
@Component
public class ProductEventPublisher {

    private final EventPublisher publisher;

    public ProductEventPublisher(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductChanged(ProductChangedEvent event) {
        String key = String.valueOf(event.productId());
        switch (event.type()) {
            case CREATED -> publisher.publish("productEvents-out-0", key, "ProductCreated", event.payload());
            case UPDATED -> publisher.publish("productEvents-out-0", key, "ProductUpdated", event.payload());
            case DELETED -> publisher.publish("productEvents-out-0", key, "ProductDeleted", Map.of("id", event.productId()));
        }
    }
}
