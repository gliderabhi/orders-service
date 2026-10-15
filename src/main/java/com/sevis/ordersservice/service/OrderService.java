package com.sevis.ordersservice.service;

import com.sevis.ordersservice.dto.request.OrderRequest;
import com.sevis.ordersservice.dto.response.OrderResponse;
import com.sevis.ordersservice.model.Order;
import com.sevis.ordersservice.model.mapper.OrderMapper;
import com.sevis.ordersservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(OrderMapper::toResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderMapper.toResponse(order);
    }

    public List<OrderResponse> getByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderMapper::toResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse create(OrderRequest request) {
        Order order = OrderMapper.toEntity(request);
        return OrderMapper.toResponse(orderRepository.save(order));
    }

    public OrderResponse updateStatus(Long id, String status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        order.setStatus(status);
        return OrderMapper.toResponse(orderRepository.save(order));
    }
}
