package hu.unimiskolc.shopstream.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InventoryEventsListenerTest {

    @Mock
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InventoryEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new InventoryEventsListener(orderService);
    }

    @Test
    void confirmsTheOrderWhenStockWasReserved() throws Exception {
        listener.handleInventoryEvent(event("""
                {"eventId":"e1","eventType":"StockReserved","payload":{"orderId":12,"status":"RESERVED"}}
                """));

        verify(orderService).applyInventoryResult(12L, true, null);
    }

    @Test
    void rejectsTheOrderWithTheReason() throws Exception {
        listener.handleInventoryEvent(event("""
                {"eventId":"e2","eventType":"StockRejected","payload":{"orderId":13,"reason":"Insufficient stock"}}
                """));

        verify(orderService).applyInventoryResult(13L, false, "Insufficient stock");
    }

    @Test
    void ignoresOtherEventTypes() throws Exception {
        listener.handleInventoryEvent(event("""
                {"eventId":"e3","eventType":"SomethingElse","payload":{"orderId":14}}
                """));

        verifyNoInteractions(orderService);
    }

    private EventMessage event(String json) throws Exception {
        return objectMapper.readValue(json, EventMessage.class);
    }
}
