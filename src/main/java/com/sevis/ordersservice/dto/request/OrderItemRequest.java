package com.sevis.ordersservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class OrderItemRequest {

    @NotNull
    private Long inventoryItemId;

    @Min(1)
    private int quantity;

    @Positive
    private double unitPrice;
}
