package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.dto.response.JobCardSummaryResponse;
import com.sevis.ordersservice.model.Vehicle;
import com.sevis.ordersservice.repository.JobCardRepository;
import com.sevis.ordersservice.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Vehicles are slow-changing master data (regNumber/make/model/etc) created
// as a side effect of JobCardService.create()/InvoiceService.uploadPdf — not
// updated in place here. We deliberately do NOT wire cross-class eviction
// from those write paths: a newly created vehicle may take up to the cache's
// TTL to show up in getAll's list, which is an accepted, bounded staleness
// window for master data (not a live status field). getHistory embeds each
// job card's mutable status, so — per the conservative order-status rule —
// it gets a short TTL as a backstop rather than a longer one, even though no
// write endpoint here evicts it directly.
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleRepository vehicleRepository;
    private final JobCardRepository jobCardRepository;

    @Cacheable(value = "vehicleList", key = "#userId + '-' + #userRole", sync = true)
    @GetMapping
    public List<Vehicle> getAll(
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        if ("ADMIN".equals(userRole)) return vehicleRepository.findAll();
        return vehicleRepository.findByDealerId(userId);
    }

    @Cacheable(value = "vehicleById", key = "#id", sync = true)
    @GetMapping("/{id}")
    public Vehicle getById(@PathVariable Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    @Cacheable(value = "vehicleHistory", key = "#id", sync = true)
    @GetMapping("/{id}/history")
    public List<JobCardSummaryResponse> getHistory(@PathVariable Long id) {
        return jobCardRepository.findByVehicleId(id)
                .stream().map(JobCardSummaryResponse::new).toList();
    }
}
