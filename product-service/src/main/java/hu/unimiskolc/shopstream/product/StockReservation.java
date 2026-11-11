package hu.unimiskolc.shopstream.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/**
 * One row per order. It makes the OrderCreated consumer idempotent: a redelivered
 * Kafka record finds the reservation and only republishes the previous result.
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected StockReservation() {
    }

    public StockReservation(Long orderId, ReservationStatus status, String reason) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public ReservationResult toResult(List<ReservationResult.ReservedItem> items) {
        return new ReservationResult(orderId, status.name(), items, reason);
    }

    public Long getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
