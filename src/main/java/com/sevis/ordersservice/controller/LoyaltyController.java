package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.model.Customer;
import com.sevis.ordersservice.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final CustomerRepository customerRepository;

    @GetMapping("/customer/{id}")
    public Map<String, Object> getByCustomerId(@PathVariable Long id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        return loyaltyMap(c);
    }

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
