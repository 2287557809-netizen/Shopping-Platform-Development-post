package hu.unimiskolc.shopstream.order;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Step 2, tasks 1-2: topics are declared from Java with TopicBuilder, three
 * partitions each. KafkaAdmin creates them at startup if they do not exist.
 * The names are read from config-server (app.topics.*).
 */
@Configuration
@EnableKafka
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;

    @Value("${app.topics.order-events:order-events}")
    private String orderEvents;

    @Value("${app.topics.inventory-events:inventory-events}")
    private String inventoryEvents;

    @Value("${app.topics.product-events:product-events}")
    private String productEvents;

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(orderEvents)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(inventoryEvents)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic productEventsTopic() {
        return TopicBuilder.name(productEvents)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }
}
