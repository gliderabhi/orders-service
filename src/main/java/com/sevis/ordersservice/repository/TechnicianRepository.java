package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.Technician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    Optional<Technician> findByNameIgnoreCaseAndPanNumber(String name, String panNumber);

    Optional<Technician> findByNameIgnoreCaseAndAadhaarNumber(String name, String aadhaarNumber);
}
