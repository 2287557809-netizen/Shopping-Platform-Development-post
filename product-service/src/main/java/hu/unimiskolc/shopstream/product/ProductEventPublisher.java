package hu.unimiskolc.shopstream.product;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Kafka is contacted only after the database transaction commits, so a rolled
 * back product change is never announced to the other services.
 */
@Component
public class ProductEventPublisher {

    private final EventPublisher publisher;

    @Value("${app.topics.product-events:product-events}")
    private String productEventsTopic;

    public ProductEventPublisher(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductChanged(ProductChangedEvent event) {
        String key = String.valueOf(event.productId());
        switch (event.type()) {
            case CREATED -> publisher.publish(productEventsTopic, key, "ProductCreated", event.payload());
            case UPDATED -> publisher.publish(productEventsTopic, key, "ProductUpdated", event.payload());
            case DELETED -> publisher.publish(productEventsTopic, key, "ProductDeleted",
                    Map.of("id", event.productId()));
        }
    }
}
