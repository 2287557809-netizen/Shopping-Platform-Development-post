package hu.unimiskolc.shopstream.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationListenerTest {

    private NotificationStore store;
    private NotificationListener listener;

    @BeforeEach
    void setUp() {
        store = new NotificationStore();
        listener = new NotificationListener(new ObjectMapper(), store);
    }

    @Test
    void storesAnOrderCreatedNotification() {
        listener.orderEvents().accept("""
                {"eventId":"e1","eventType":"OrderCreated","payload":{"orderId":10,"totalPrice":42.5}}
                """);

        var notifications = store.latest(10);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).eventType()).isEqualTo("OrderCreated");
        assertThat(notifications.get(0).orderId()).isEqualTo(10L);
        assertThat(notifications.get(0).message()).contains("Order #10");
    }

    @Test
    void ignoresRedeliveredEventsWithTheSameEventId() {
        String event = """
                {"eventId":"e2","eventType":"StockReserved","payload":{"orderId":11}}
                """;

        listener.inventoryEvents().accept(event);
        listener.inventoryEvents().accept(event);

        assertThat(store.latest(10)).hasSize(1);
    }

    @Test
    void storesStockRejectionReasons() {
        listener.inventoryEvents().accept("""
                {"eventId":"e3","eventType":"StockRejected","payload":{"orderId":12,"reason":"No stock"}}
                """);

        assertThat(store.latest(10).get(0).message()).contains("No stock");
    }

    @Test
    void productEventsHaveNoOrderId() {
        listener.productEvents().accept("""
                {"eventId":"e4","eventType":"ProductCreated","payload":{"id":5,"name":"Laptop"}}
                """);

        assertThat(store.latest(10).get(0).orderId()).isNull();
    }

    @Test
    void ignoresUnknownEventTypes() {
        listener.orderEvents().accept("""
                {"eventId":"e5","eventType":"SomethingElse","payload":{}}
                """);

        assertThat(store.latest(10)).isEmpty();
    }
}
