package hu.unimiskolc.shopstream.order;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final ProductClient productClient;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository repository,
                        ProductClient productClient,
                        ApplicationEventPublisher events) {
        this.repository = repository;
        this.productClient = productClient;
        this.events = events;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new InvalidOrderException("An order needs at least one item");
        }

        Order order = new Order();
        BigDecimal total = BigDecimal.ZERO;
        List<OrderItemPayload> eventItems = new ArrayList<>();

        for (OrderItemRequest itemRequest : request.items()) {
            if (itemRequest.productId() == null || itemRequest.quantity() == null
                    || itemRequest.quantity() <= 0) {
                throw new InvalidOrderException("productId and a positive quantity are required");
            }

            ProductDto product;
            try {
                product = productClient.getProduct(itemRequest.productId());
            } catch (FeignException.NotFound ex) {
                throw new ProductNotFoundException(itemRequest.productId());
            } catch (FeignException ex) {
                throw new ProductServiceUnavailableException(
                        "product-service is unavailable", ex);
            }

            if (product == null || product.price() == null) {
                throw new ProductNotFoundException(itemRequest.productId());
            }

            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(product.price());
            order.addItem(item);

            total = total.add(product.price().multiply(BigDecimal.valueOf(itemRequest.quantity())));
            eventItems.add(new OrderItemPayload(product.id(), itemRequest.quantity()));
        }

        order.setTotalPrice(total);
        order.setStatus(OrderStatus.PENDING);
        Order saved = repository.save(order);

        // The transactional listener publishes only after this transaction commits.
        events.publishEvent(new OrderCreatedEvent(saved.getId(), eventItems, total));

        log.info("Order {} created with {} item(s), total {}", saved.getId(), eventItems.size(), total);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return repository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll(OrderStatus status) {
        List<Order> orders = status == null
                ? repository.findAllByOrderByIdDesc()
                : repository.findByStatusOrderByIdDesc(status);
        return orders.stream().map(OrderResponse::from).toList();
    }

    /**
     * Applies the result of the inventory saga. Only a PENDING order can change
     * state, which makes duplicate StockReserved/StockRejected deliveries safe.
     */
    @Transactional
    public void applyInventoryResult(Long orderId, boolean reserved, String reason) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is already {}, ignoring duplicate inventory result",
                    orderId, order.getStatus());
            return;
        }

        if (reserved) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setFailureReason(null);
        } else {
            order.setStatus(OrderStatus.REJECTED);
            order.setFailureReason(reason == null ? "Stock reservation failed" : reason);
        }

        Order saved = repository.save(order);
        events.publishEvent(new OrderStatusChangedEvent(
                saved.getId(), saved.getStatus(), saved.getFailureReason()));

        log.info("Order {} moved to {}", orderId, saved.getStatus());
    }
}
