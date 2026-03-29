package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByRegNumber(String regNumber);
}
