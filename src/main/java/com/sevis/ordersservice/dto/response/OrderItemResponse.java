package com.sevis.ordersservice.dto.response;

import com.sevis.ordersservice.model.OrderItem;
import lombok.Getter;

@Getter
public class OrderItemResponse {

    private final Long id;
    private final Long inventoryItemId;
    private final int quantity;
    private final double unitPrice;

    public OrderItemResponse(OrderItem item) {
        this.id = item.getId();
        this.inventoryItemId = item.getInventoryItemId();
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();
    }
}
