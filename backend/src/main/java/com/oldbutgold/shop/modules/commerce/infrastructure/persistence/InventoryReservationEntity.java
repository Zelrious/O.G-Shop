package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations", schema = "og_compat")
public class InventoryReservationEntity {

    public static final String STATE_HELD = "HELD";
    public static final String STATE_COMMITTED = "COMMITTED";
    public static final String STATE_RELEASED = "RELEASED";

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "state", nullable = false, length = 15)
    private String state = STATE_HELD;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(name = "command_key", nullable = false, length = 150)
    private String commandKey;

    protected InventoryReservationEntity() {}

    public InventoryReservationEntity(UUID reservationId, Long productId, Long orderItemId,
                                      int quantity, String state, Instant createdAt,
                                      Instant expiresAt, String commandKey) {
        this.reservationId = reservationId != null ? reservationId : UUID.randomUUID();
        this.productId = productId;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
        this.state = state != null ? state : STATE_HELD;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = expiresAt;
        this.changedAt = this.createdAt;
        this.commandKey = commandKey;
    }

    public UUID getReservationId() { return reservationId; }
    public void setReservationId(UUID reservationId) { this.reservationId = reservationId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public String getCommandKey() { return commandKey; }
    public void setCommandKey(String commandKey) { this.commandKey = commandKey; }
}

