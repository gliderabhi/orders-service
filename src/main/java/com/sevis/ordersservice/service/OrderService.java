package com.sevis.ordersservice.service;

import com.sevis.ordersservice.dto.request.OrderRequest;
import com.sevis.ordersservice.dto.response.OrderResponse;
import com.sevis.ordersservice.model.Order;
import com.sevis.ordersservice.model.mapper.OrderMapper;
import com.sevis.ordersservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

// Order status is exactly the kind of field a customer/dealer expects to see
// update instantly, so every read here carries only a short (20s) TTL — see
// CacheConfig — and every write below evicts the relevant cache(s) immediately
// rather than relying on the TTL to catch up.
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Cacheable(value = "orderList", sync = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(OrderMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "orderById", key = "#id", sync = true)
    public OrderResponse getById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderMapper.toResponse(order);
    }

    @Cacheable(value = "ordersByUser", key = "#userId", sync = true)
    public List<OrderResponse> getByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Caching(evict = {
            @CacheEvict(value = "orderList", allEntries = true),
            @CacheEvict(value = "ordersByUser", allEntries = true)
    })
    public OrderResponse create(OrderRequest request) {
        Order order = OrderMapper.toEntity(request);
        return OrderMapper.toResponse(orderRepository.save(order));
    }

    @Caching(evict = {
            @CacheEvict(value = "orderById", key = "#id"),
            @CacheEvict(value = "orderList", allEntries = true),
            @CacheEvict(value = "ordersByUser", allEntries = true)
    })
    public OrderResponse updateStatus(Long id, String status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        order.setStatus(status);
        return OrderMapper.toResponse(orderRepository.save(order));
    }
}
