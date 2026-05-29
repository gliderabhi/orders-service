package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.dto.response.AuditSummaryResponse;
import com.sevis.ordersservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/summary")
    public AuditSummaryResponse getSummary(
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        Long dealerId = "ADMIN".equals(userRole) ? null : userId;
        return auditService.getSummary(dealerId);
    }
}
