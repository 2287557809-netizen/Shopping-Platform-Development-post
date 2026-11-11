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

    private InventoryEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new InventoryEventsListener(new ObjectMapper(), orderService);
    }

    @Test
    void confirmsTheOrderWhenStockWasReserved() {
        listener.handleInventoryEvent("""
                {"eventType":"StockReserved","payload":{"orderId":12,"status":"RESERVED"}}
                """);

        verify(orderService).applyInventoryResult(12L, true, null);
    }

    @Test
    void rejectsTheOrderWithTheReason() {
        listener.handleInventoryEvent("""
                {"eventType":"StockRejected","payload":{"orderId":13,"reason":"Insufficient stock"}}
                """);

        verify(orderService).applyInventoryResult(13L, false, "Insufficient stock");
    }

    @Test
    void ignoresOtherEventTypes() {
        listener.handleInventoryEvent("""
                {"eventType":"SomethingElse","payload":{"orderId":14}}
                """);

        verifyNoInteractions(orderService);
    }

    @Test
    void dropsMalformedJson() {
        listener.handleInventoryEvent("not-json");

        verifyNoInteractions(orderService);
    }
}
