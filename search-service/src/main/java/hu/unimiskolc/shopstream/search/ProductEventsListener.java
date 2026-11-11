package hu.unimiskolc.shopstream.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class ProductEventsListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventsListener.class);

    private final ObjectMapper objectMapper;
    private final ProductSearchRepository repository;

    public ProductEventsListener(ObjectMapper objectMapper, ProductSearchRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Bean
    public Consumer<String> productEvents() {
        return this::handleProductEvent;
    }

    void handleProductEvent(String message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (JsonProcessingException ex) {
            log.error("Dropping malformed product event: {}", message, ex);
            return;
        }

        String eventType = root.path("eventType").asText();
        JsonNode payload = root.path("payload");

        if ("ProductDeleted".equals(eventType)) {
            Long id = payload.path("id").asLong();
            if (repository.existsById(id)) {
                repository.deleteById(id);
            }
            log.info("Removed product {} from the search index", id);
            return;
        }

        if (!"ProductCreated".equals(eventType) && !"ProductUpdated".equals(eventType)) {
            return;
        }

        try {
            ProductDocument document = objectMapper.treeToValue(payload, ProductDocument.class);
            repository.save(document);
            log.info("Indexed product {} ({})", document.getId(), eventType);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Dropping invalid product payload: {}", payload, ex);
        }
    }
}
