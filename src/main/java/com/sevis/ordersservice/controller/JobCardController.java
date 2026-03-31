package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.dto.request.AncillaryItemRequest;
import com.sevis.ordersservice.dto.request.CreateJobCardRequest;
import com.sevis.ordersservice.dto.request.LabourItemRequest;
import com.sevis.ordersservice.dto.request.PartItemRequest;
import com.sevis.ordersservice.dto.response.InvoiceDetailResponse;
import com.sevis.ordersservice.dto.response.JobCardDetailResponse;
import com.sevis.ordersservice.dto.response.JobCardSummaryResponse;
import com.sevis.ordersservice.service.InvoiceService;
import com.sevis.ordersservice.service.JobCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-cards")
@RequiredArgsConstructor
public class JobCardController {

    private final JobCardService  jobCardService;
    private final InvoiceService  invoiceService;

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

    // ── Download bill PDF ─────────────────────────────────────────────────────

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = jobCardService.generatePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("bill-" + id + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    // ── Generate / update invoice from job card ───────────────────────────────

    @PostMapping("/{id}/invoice")
    public ResponseEntity<InvoiceDetailResponse> generateInvoice(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long dealerId
    ) {
        return ResponseEntity.ok(invoiceService.generateOrUpdateInvoice(id, dealerId));
    }

    // ── Labour CRUD ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/labour")
    public ResponseEntity<JobCardDetailResponse> addLabour(
            @PathVariable Long id,
            @RequestBody LabourItemRequest req,
            @RequestHeader(value = "X-User-Id",   defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "")  String userRole
    ) {
        return ResponseEntity.ok(jobCardService.addLabour(id, req, "ADMIN".equals(userRole) ? null : userId));
    }

    @DeleteMapping("/{id}/labour/{labourId}")
    public ResponseEntity<JobCardDetailResponse> deleteLabour(
            @PathVariable Long id, @PathVariable Long labourId,
            @RequestHeader(value = "X-User-Id",   defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "")  String userRole
    ) {
        return ResponseEntity.ok(jobCardService.deleteLabour(id, labourId, "ADMIN".equals(userRole) ? null : userId));
    }

    // ── Parts CRUD ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/parts")
    public ResponseEntity<JobCardDetailResponse> addPart(
            @PathVariable Long id,
            @RequestBody PartItemRequest req,
            @RequestHeader(value = "X-User-Id",   defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "")  String userRole
    ) {
        return ResponseEntity.ok(jobCardService.addPart(id, req, "ADMIN".equals(userRole) ? null : userId));
    }

    @DeleteMapping("/{id}/parts/{partId}")
    public ResponseEntity<JobCardDetailResponse> deletePart(
            @PathVariable Long id, @PathVariable Long partId,
            @RequestHeader(value = "X-User-Id",   defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "")  String userRole
    ) {
        return ResponseEntity.ok(jobCardService.deletePart(id, partId, "ADMIN".equals(userRole) ? null : userId));
    }

    // ── Ancillary CRUD ────────────────────────────────────────────────────────

    @PostMapping("/{id}/ancillary")
    public ResponseEntity<JobCardDetailResponse> addAncillary(
            @PathVariable Long id,
            @RequestBody AncillaryItemRequest req,
            @RequestHeader(value = "X-User-Id",   defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "")  String userRole
    ) {
        return ResponseEntity.ok(jobCardService.addAncillary(id, req, "ADMIN".equals(userRole) ? null : userId));
    }

    @DeleteMapping("/{id}/ancillary/{ancId}")
    public ResponseEntity<JobCardDetailResponse> deleteAncillary(
            @PathVariable Long id, @PathVariable Long ancId,
            @RequestHeader(value = "X-User-Id",   defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "")  String userRole
    ) {
        return ResponseEntity.ok(jobCardService.deleteAncillary(id, ancId, "ADMIN".equals(userRole) ? null : userId));
    }
}
