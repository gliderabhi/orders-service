package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.model.Customer;
import com.sevis.ordersservice.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// Loyalty points/tier behave like an account balance — mutable, but not the
// kind of instant-feedback field order/job-card status is. A short TTL (30s,
// see CacheConfig) plus explicit eviction from InvoiceService.awardLoyaltyPoints
// (evicted programmatically via CacheManager since that's a different class)
// keeps this close to live without needing a self-injection split here.
@RestController
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final CustomerRepository customerRepository;

    @Cacheable(value = "loyaltyByCustomer", key = "#id", sync = true)
    @GetMapping("/customer/{id}")
    public Map<String, Object> getByCustomerId(@PathVariable Long id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        return loyaltyMap(c);
    }

    @Cacheable(value = "loyaltyByPhone", key = "#phone", sync = true)
    @GetMapping("/phone/{phone}")
    public Map<String, Object> getByPhone(@PathVariable String phone) {
        Customer c = customerRepository.findByPhone(phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        return loyaltyMap(c);
    }

    private Map<String, Object> loyaltyMap(Customer c) {
        return Map.of(
            "customerId",    c.getId(),
            "name",          c.getName() != null ? c.getName() : "",
            "phone",         c.getPhone(),
            "memberNumber",  c.getLoyaltyMemberNumber() != null ? c.getLoyaltyMemberNumber() : "Not enrolled",
            "points",        c.getLoyaltyPoints() != null ? c.getLoyaltyPoints() : 0,
            "tier",          c.getLoyaltyTier() != null ? c.getLoyaltyTier() : "MEMBER"
        );
    }
}
