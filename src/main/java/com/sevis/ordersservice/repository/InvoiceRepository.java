package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByJobCardId(Long jobCardId);
    boolean existsByInvoiceNumber(String invoiceNumber);
    long countByInvoiceDate(LocalDate date);

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i WHERE i.invoiceDate BETWEEN :start AND :end")
    double sumGrandTotalBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.invoiceDate BETWEEN :start AND :end")
    long countByInvoiceDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i")
    double sumGrandTotalAll();
}
