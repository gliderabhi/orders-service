package com.sevis.ordersservice.model.mapper;

import com.sevis.ordersservice.dto.request.OrderItemRequest;
import com.sevis.ordersservice.dto.request.OrderRequest;
import com.sevis.ordersservice.dto.response.OrderResponse;
import com.sevis.ordersservice.model.Order;
import com.sevis.ordersservice.model.OrderItem;

import java.util.List;
import java.util.stream.Collectors;

public class OrderMapper {

    public static Order toEntity(OrderRequest request) {
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus(request.getStatus());

        List<OrderItem> items = request.getItems().stream()
                .map(itemRequest -> toItemEntity(itemRequest, order))
                .collect(Collectors.toList());

        order.setItems(items);

        double total = items.stream()
                .mapToDouble(item -> item.getUnitPrice() * item.getQuantity())
                .sum();
        order.setTotalAmount(total);

        return order;
    }

    private static OrderItem toItemEntity(OrderItemRequest request, Order order) {
        OrderItem item = new OrderItem();
        item.setInventoryItemId(request.getInventoryItemId());
        item.setQuantity(request.getQuantity());
        item.setUnitPrice(request.getUnitPrice());
        item.setOrder(order);
        return item;
    }

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(order);
    }

    private OrderMapper() {}
}
