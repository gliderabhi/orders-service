package com.sevis.ordersservice.dto.response;

import com.sevis.ordersservice.model.Order;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class OrderResponse {

    private final Long id;
    private final Long userId;
    private final List<OrderItemResponse> items;
    private final double totalAmount;
    private final String status;
    private final LocalDateTime createdAt;

    public OrderResponse(Order order) {
        this.id = order.getId();
        this.userId = order.getUserId();
        this.items = order.getItems().stream()
                .map(OrderItemResponse::new)
                .collect(Collectors.toList());
        this.totalAmount = order.getTotalAmount();
        this.status = order.getStatus();
        this.createdAt = order.getCreatedAt();
    }
}
