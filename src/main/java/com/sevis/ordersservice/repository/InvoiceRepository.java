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
    List<Invoice> findByDealerIdOrderByInvoiceDateDesc(Long dealerId);

    @Query("SELECT i FROM Invoice i WHERE (:from IS NULL OR i.invoiceDate >= :from) AND (:to IS NULL OR i.invoiceDate <= :to) ORDER BY i.invoiceDate DESC")
    List<Invoice> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT i FROM Invoice i WHERE i.dealerId = :dealerId AND (:from IS NULL OR i.invoiceDate >= :from) AND (:to IS NULL OR i.invoiceDate <= :to) ORDER BY i.invoiceDate DESC")
    List<Invoice> findByDealerIdAndDateRange(@Param("dealerId") Long dealerId, @Param("from") LocalDate from, @Param("to") LocalDate to);
    boolean existsByInvoiceNumber(String invoiceNumber);
    long countByInvoiceDate(LocalDate date);

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i WHERE i.invoiceDate BETWEEN :start AND :end")
    double sumGrandTotalBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i WHERE i.invoiceDate BETWEEN :start AND :end AND i.jobCard.dealerId = :dealerId")
    double sumGrandTotalBetweenByDealer(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("dealerId") Long dealerId);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.invoiceDate BETWEEN :start AND :end")
    long countByInvoiceDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.invoiceDate BETWEEN :start AND :end AND i.jobCard.dealerId = :dealerId")
    long countByInvoiceDateBetweenAndDealer(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("dealerId") Long dealerId);

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i")
    double sumGrandTotalAll();

    @Query("SELECT COALESCE(SUM(i.grandTotal), 0) FROM Invoice i WHERE i.jobCard.dealerId = :dealerId")
    double sumGrandTotalAllByDealer(@Param("dealerId") Long dealerId);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.jobCard.dealerId = :dealerId")
    long countByDealer(@Param("dealerId") Long dealerId);
}
