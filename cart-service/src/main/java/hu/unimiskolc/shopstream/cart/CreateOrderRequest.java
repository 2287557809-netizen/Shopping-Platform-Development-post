package hu.unimiskolc.shopstream.cart;

import java.util.List;

public record CreateOrderRequest(List<OrderItemRequest> items) {
}
