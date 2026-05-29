package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.dto.response.JobCardSummaryResponse;
import com.sevis.ordersservice.model.Vehicle;
import com.sevis.ordersservice.repository.JobCardRepository;
import com.sevis.ordersservice.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleRepository vehicleRepository;
    private final JobCardRepository jobCardRepository;

    @GetMapping
    public List<Vehicle> getAll(
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        if ("ADMIN".equals(userRole)) return vehicleRepository.findAll();
        return vehicleRepository.findByDealerId(userId);
    }

    @GetMapping("/{id}")
    public Vehicle getById(@PathVariable Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    @GetMapping("/{id}/history")
    public List<JobCardSummaryResponse> getHistory(@PathVariable Long id) {
        return jobCardRepository.findByVehicleId(id)
                .stream().map(JobCardSummaryResponse::new).toList();
    }
}
