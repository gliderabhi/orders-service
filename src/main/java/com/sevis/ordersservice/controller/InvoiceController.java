package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.dto.response.InvoiceDetailResponse;
import com.sevis.ordersservice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/upload")
    public ResponseEntity<InvoiceDetailResponse> upload(
            @RequestBody byte[] pdfBytes,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long dealerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceService.uploadPdf(pdfBytes, dealerId));
    }

    @GetMapping("/job-card/{jobCardId}")
    public List<InvoiceDetailResponse> getByJobCard(@PathVariable Long jobCardId) {
        return invoiceService.getByJobCard(jobCardId);
    }

    @GetMapping("/{id}")
    public InvoiceDetailResponse getById(@PathVariable Long id) {
        return invoiceService.getById(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = invoiceService.generatePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("invoice-" + id + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
