package hu.unimiskolc.shopstream.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class XRequestIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(XRequestIdFilter.class);
    private static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        final String resolvedRequestId = requestId;

        long startedAt = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER, resolvedRequestId)
                .build();

        exchange.getResponse().getHeaders().set(HEADER, resolvedRequestId);

        return chain.filter(exchange.mutate().request(request).build())
                .then(Mono.fromRunnable(() -> log.info(
                        "{} {} -> {} ({} ms) requestId={}",
                        exchange.getRequest().getMethod(),
                        path,
                        exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - startedAt,
                        resolvedRequestId)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
