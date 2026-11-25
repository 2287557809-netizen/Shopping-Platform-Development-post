package hu.unimiskolc.shopstream.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationStore store;
    private NotificationListener listener;

    @BeforeEach
    void setUp() {
        store = new NotificationStore();
        listener = new NotificationListener(store);
    }

    @Test
    void storesAnOrderPlacedNotification() throws Exception {
        listener.onOrderEvent(record("""
                {"eventId":"e1","eventType":"OrderPlaced","payload":{"orderId":10,"totalPrice":42.5}}
                """));

        var notifications = store.latest(10);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).eventType()).isEqualTo("OrderPlaced");
        assertThat(notifications.get(0).orderId()).isEqualTo(10L);
        assertThat(notifications.get(0).message()).contains("Order #10");
    }

    @Test
    void ignoresRedeliveredEventsWithTheSameEventId() throws Exception {
        ConsumerRecord<String, EventMessage> record = record("""
                {"eventId":"e2","eventType":"StockReserved","payload":{"orderId":11}}
                """);

        listener.onInventoryEvent(record);
        listener.onInventoryEvent(record);

        assertThat(store.latest(10)).hasSize(1);
    }

    @Test
    void storesStockRejectionReasons() throws Exception {
        listener.onInventoryEvent(record("""
                {"eventId":"e3","eventType":"StockRejected","payload":{"orderId":12,"reason":"No stock"}}
                """));

        assertThat(store.latest(10).get(0).message()).contains("No stock");
    }

    @Test
    void ignoresUnknownEventTypes() throws Exception {
        listener.onOrderEvent(record("""
                {"eventId":"e4","eventType":"SomethingElse","payload":{}}
                """));

        assertThat(store.latest(10)).isEmpty();
    }

    private ConsumerRecord<String, EventMessage> record(String json) throws Exception {
        EventMessage event = objectMapper.readValue(json, EventMessage.class);
        return new ConsumerRecord<>("order-events", 0, 0L, "1", event);
    }
}
