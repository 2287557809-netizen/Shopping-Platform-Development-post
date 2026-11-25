package hu.unimiskolc.shopstream.cart;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One line of the cart. It is stored inside the cart hash, so it has to be
 * serialisable; it does not need its own Redis key.
 */
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;
    private String name;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
