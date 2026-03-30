package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByJobCardId(Long jobCardId);
    boolean existsByInvoiceNumber(String invoiceNumber);
}
