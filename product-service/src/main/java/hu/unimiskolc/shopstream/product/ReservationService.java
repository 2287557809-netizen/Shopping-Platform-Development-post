package hu.unimiskolc.shopstream.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final ProductRepository productRepository;
    private final StockReservationRepository reservationRepository;

    public ReservationService(ProductRepository productRepository,
                              StockReservationRepository reservationRepository) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Locks products in a deterministic order (by id) to avoid deadlocks between
     * two orders that contain the same products in a different sequence.
     */
    @Transactional
    public ReservationResult reserve(OrderCreatedPayload payload) {
        if (payload.orderId() == null) {
            throw new InsufficientStockException("Order id is missing");
        }
        if (payload.items() == null || payload.items().isEmpty()) {
            throw new InsufficientStockException("Order " + payload.orderId() + " has no items");
        }

        var existing = reservationRepository.findById(payload.orderId());
        if (existing.isPresent()) {
            log.info("Order {} already processed, republishing {}", payload.orderId(), existing.get().getStatus());
            return existing.get().toResult(List.of());
        }

        List<OrderItemPayload> items = new ArrayList<>(payload.items());
        items.sort(Comparator.comparing(OrderItemPayload::productId,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        List<ReservationResult.ReservedItem> reserved = new ArrayList<>();
        for (OrderItemPayload item : items) {
            if (item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
                throw new InsufficientStockException("Invalid item in order " + payload.orderId());
            }

            Product product = productRepository.findByIdForUpdate(item.productId())
                    .orElseThrow(() -> new InsufficientStockException(
                            "Product " + item.productId() + " does not exist"));

            int available = product.getStock() == null ? 0 : product.getStock();
            if (available < item.quantity()) {
                throw new InsufficientStockException("Insufficient stock for product "
                        + item.productId() + ": requested " + item.quantity() + ", available " + available);
            }

            product.setStock(available - item.quantity());
            productRepository.save(product);
            reserved.add(new ReservationResult.ReservedItem(
                    product.getId(), item.quantity(), product.getStock()));
        }

        reservationRepository.save(new StockReservation(
                payload.orderId(), ReservationStatus.RESERVED, null));

        log.info("Reserved stock for order {}: {}", payload.orderId(), reserved);
        return new ReservationResult(payload.orderId(), "RESERVED", reserved, null);
    }

    /**
     * A rejection is stored in a separate transaction because the reservation
     * transaction has already been rolled back when this method is called.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResult reject(Long orderId, String reason) {
        var existing = reservationRepository.findById(orderId);
        if (existing.isPresent()) {
            return existing.get().toResult(List.of());
        }

        String safeReason = reason == null ? "Stock reservation failed" : reason;
        if (safeReason.length() > MAX_REASON_LENGTH) {
            safeReason = safeReason.substring(0, MAX_REASON_LENGTH);
        }

        reservationRepository.save(new StockReservation(orderId, ReservationStatus.REJECTED, safeReason));
        log.info("Rejected order {}: {}", orderId, safeReason);
        return new ReservationResult(orderId, "REJECTED", List.of(), safeReason);
    }
}
