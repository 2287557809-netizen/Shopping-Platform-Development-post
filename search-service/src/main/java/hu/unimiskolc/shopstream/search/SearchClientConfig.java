package hu.unimiskolc.shopstream.search;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot creates its own {@link JacksonJsonpMapper} backed by a plain
 * ObjectMapper. Two of its defaults break the low level Elasticsearch client:
 *
 * <ul>
 *   <li>unknown properties are rejected, and documents written by Spring Data
 *       may contain a "_class" type hint;</li>
 *   <li>trailing tokens are rejected, but the client feeds the mapper a parser
 *       that is positioned inside a larger JSON stream.</li>
 * </ul>
 *
 * <p>A copy of the application ObjectMapper with those two checks disabled is
 * used for transport (de)serialization only, so HTTP request parsing keeps the
 * Spring Boot defaults.</p>
 */
@Configuration
public class SearchClientConfig {

    @Bean
    public JsonpMapper jsonpMapper(ObjectMapper objectMapper) {
        ObjectMapper transportMapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        return new JacksonJsonpMapper(transportMapper);
    }
}
