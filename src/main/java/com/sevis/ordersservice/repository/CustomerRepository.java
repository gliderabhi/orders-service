package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhone(String phone);
    boolean existsByPhone(String phone);
}
