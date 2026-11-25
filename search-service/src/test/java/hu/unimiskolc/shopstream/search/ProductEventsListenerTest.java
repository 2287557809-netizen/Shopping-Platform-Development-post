package hu.unimiskolc.shopstream.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductEventsListenerTest {

    @Mock
    private ProductSearchRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProductEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new ProductEventsListener(repository);
    }

    @Test
    void indexesCreatedProducts() throws Exception {
        listener.handleProductEvent(event("""
                {"eventId":"e1","eventType":"ProductCreated","payload":{
                  "id":1,"name":"Laptop","description":"Fast","category":"electronics",
                  "price":1299.00,"stock":5}}
                """));

        ArgumentCaptor<ProductDocument> captor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(repository).save(captor.capture());
        ProductDocument document = captor.getValue();
        assertThat(document.getId()).isEqualTo(1L);
        assertThat(document.getName()).isEqualTo("Laptop");
        assertThat(document.getCategory()).isEqualTo("electronics");
        assertThat(document.getPrice()).isEqualTo(1299.0);
        assertThat(document.getStock()).isEqualTo(5);
    }

    @Test
    void deletesRemovedProducts() throws Exception {
        when(repository.existsById(2L)).thenReturn(true);

        listener.handleProductEvent(event("""
                {"eventId":"e2","eventType":"ProductDeleted","payload":{"id":2}}
                """));

        verify(repository).deleteById(2L);
    }

    @Test
    void ignoresUnknownEventTypes() throws Exception {
        listener.handleProductEvent(event("""
                {"eventId":"e3","eventType":"SomethingElse","payload":{"id":3}}
                """));

        verifyNoInteractions(repository);
    }

    private EventMessage event(String json) throws Exception {
        return objectMapper.readValue(json, EventMessage.class);
    }
}
