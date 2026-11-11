package hu.unimiskolc.shopstream.cart;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service", path = "/orders")
public interface OrderClient {

    @PostMapping
    OrderResponse create(@RequestBody CreateOrderRequest request);
}
