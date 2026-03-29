package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.dto.request.CreateJobCardRequest;
import com.sevis.ordersservice.dto.response.JobCardDetailResponse;
import com.sevis.ordersservice.dto.response.JobCardSummaryResponse;
import com.sevis.ordersservice.service.JobCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-cards")
@RequiredArgsConstructor
public class JobCardController {

    private final JobCardService jobCardService;

    @GetMapping
    public List<JobCardSummaryResponse> getAll(
            @RequestHeader(value = "X-User-Id",   defaultValue = "0")    Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "")     String userRole
    ) {
        // Admins see all; dealers see only their own
        Long dealerFilter = "ADMIN".equals(userRole) ? null : userId;
        return jobCardService.getAll(dealerFilter);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobCardDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(jobCardService.getById(id));
    }

    @PostMapping
    public ResponseEntity<JobCardDetailResponse> create(
            @Valid @RequestBody CreateJobCardRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long dealerId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobCardService.create(request, dealerId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<JobCardDetailResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestHeader(value = "X-User-Id",   defaultValue = "0")    Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "")     String userRole
    ) {
        Long dealerFilter = "ADMIN".equals(userRole) ? null : userId;
        return ResponseEntity.ok(jobCardService.updateStatus(id, status, dealerFilter));
    }
}
