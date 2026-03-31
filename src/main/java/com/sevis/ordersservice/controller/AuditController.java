package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.dto.response.AuditSummaryResponse;
import com.sevis.ordersservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/summary")
    public AuditSummaryResponse getSummary() {
        return auditService.getSummary();
    }
}
