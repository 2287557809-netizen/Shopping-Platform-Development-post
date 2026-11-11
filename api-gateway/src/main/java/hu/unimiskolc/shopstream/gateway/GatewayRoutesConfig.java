package hu.unimiskolc.shopstream.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes are declared in Java instead of application.yml so the mapping is
 * type-checked and lives next to the gateway code.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator shopStreamRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("product-route", route -> route
                        .path("/api/products/**")
                        .filters(filters -> filters.stripPrefix(1))
                        .uri("lb://product-service"))
                .route("order-route", route -> route
                        .path("/api/orders/**")
                        .filters(filters -> filters.stripPrefix(1))
                        .uri("lb://order-service"))
                .route("cart-route", route -> route
                        .path("/api/carts/**")
                        .filters(filters -> filters.stripPrefix(1))
                        .uri("lb://cart-service"))
                .route("search-route", route -> route
                        .path("/api/search/**")
                        .filters(filters -> filters.stripPrefix(1))
                        .uri("lb://search-service"))
                .route("notification-route", route -> route
                        .path("/api/notifications/**")
                        .filters(filters -> filters.stripPrefix(1))
                        .uri("lb://notification-service"))
                .build();
    }
}
