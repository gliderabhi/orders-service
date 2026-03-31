package com.sevis.ordersservice.service;

import com.sevis.ordersservice.dto.response.AuditSummaryResponse;
import com.sevis.ordersservice.repository.InvoiceRepository;
import com.sevis.ordersservice.repository.JobCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final JobCardRepository jobCardRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public AuditSummaryResponse getSummary() {
        long open        = jobCardRepository.countByStatus("OPEN");
        long inProgress  = jobCardRepository.countByStatus("IN_PROGRESS");
        long ready       = jobCardRepository.countByStatus("READY");
        long delivered   = jobCardRepository.countByStatus("DELIVERED");
        long closed      = jobCardRepository.countByStatus("CLOSED");
        long total       = open + inProgress + ready + delivered + closed;

        double labourTotal     = jobCardRepository.sumLabourTotal();
        double partsTotal      = jobCardRepository.sumPartsTotal();
        double ancTotal        = jobCardRepository.sumAncillaryTotal();
        double totalRevenue    = jobCardRepository.sumGrandTotalDeliveredClosed();

        LocalDate today          = LocalDate.now();
        LocalDate startThisMonth = today.withDayOfMonth(1);
        LocalDate startLastMonth = startThisMonth.minusMonths(1);
        LocalDate endLastMonth   = startThisMonth.minusDays(1);

        double revenueThisMonth   = invoiceRepository.sumGrandTotalBetween(startThisMonth, today);
        long   invThisMonth       = invoiceRepository.countByInvoiceDateBetween(startThisMonth, today);
        double revenuePrevMonth   = invoiceRepository.sumGrandTotalBetween(startLastMonth, endLastMonth);
        long   invPrevMonth       = invoiceRepository.countByInvoiceDateBetween(startLastMonth, endLastMonth);
        long   totalInvoices      = invoiceRepository.count();
        double allTimeSum         = invoiceRepository.sumGrandTotalAll();
        double avgInvoice         = totalInvoices > 0 ? allTimeSum / totalInvoices : 0.0;

        return new AuditSummaryResponse(
                open, inProgress, ready, delivered, closed, total,
                labourTotal, partsTotal, ancTotal, totalRevenue,
                revenueThisMonth, invThisMonth,
                revenuePrevMonth, invPrevMonth,
                totalInvoices, avgInvoice
        );
    }
}
