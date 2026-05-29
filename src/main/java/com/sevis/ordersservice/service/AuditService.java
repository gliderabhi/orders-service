package com.sevis.ordersservice.service;

import com.sevis.ordersservice.dto.response.AuditSummaryResponse;
import com.sevis.ordersservice.repository.InvoiceRepository;
import com.sevis.ordersservice.repository.JobCardRepository;
import com.sevis.ordersservice.repository.TechnicianSalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final JobCardRepository jobCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final TechnicianSalaryRepository salaryRepository;

    private long cnt(String status, boolean scoped, Long dealerId) {
        return scoped ? jobCardRepository.countByStatusAndDealerId(status, dealerId) : jobCardRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public AuditSummaryResponse getSummary(Long dealerId) {
        boolean scoped = dealerId != null;

        long open       = cnt("RECEIVED",     scoped, dealerId) + cnt("OPEN", scoped, dealerId);
        long inProgress = cnt("IN_PROGRESS",  scoped, dealerId) + cnt("QUALITY_CHECK", scoped, dealerId);
        long ready      = cnt("READY",        scoped, dealerId);
        long delivered  = cnt("DELIVERED",    scoped, dealerId);
        long closed     = cnt("CLOSED",       scoped, dealerId);
        long total      = open + inProgress + ready + delivered + closed;

        double labourTotal  = scoped ? jobCardRepository.sumLabourTotalByDealer(dealerId)              : jobCardRepository.sumLabourTotal();
        double partsTotal   = scoped ? jobCardRepository.sumPartsTotalByDealer(dealerId)               : jobCardRepository.sumPartsTotal();
        double ancTotal     = scoped ? jobCardRepository.sumAncillaryTotalByDealer(dealerId)           : jobCardRepository.sumAncillaryTotal();
        double totalRevenue = scoped ? jobCardRepository.sumGrandTotalDeliveredClosedByDealer(dealerId) : jobCardRepository.sumGrandTotalDeliveredClosed();

        LocalDate today          = LocalDate.now();
        LocalDate startThisMonth = today.withDayOfMonth(1);
        LocalDate startLastMonth = startThisMonth.minusMonths(1);
        LocalDate endLastMonth   = startThisMonth.minusDays(1);

        double revenueThisMonth = scoped ? invoiceRepository.sumGrandTotalBetweenByDealer(startThisMonth, today, dealerId)            : invoiceRepository.sumGrandTotalBetween(startThisMonth, today);
        long   invThisMonth     = scoped ? invoiceRepository.countByInvoiceDateBetweenAndDealer(startThisMonth, today, dealerId)      : invoiceRepository.countByInvoiceDateBetween(startThisMonth, today);
        double revenuePrevMonth = scoped ? invoiceRepository.sumGrandTotalBetweenByDealer(startLastMonth, endLastMonth, dealerId)     : invoiceRepository.sumGrandTotalBetween(startLastMonth, endLastMonth);
        long   invPrevMonth     = scoped ? invoiceRepository.countByInvoiceDateBetweenAndDealer(startLastMonth, endLastMonth, dealerId) : invoiceRepository.countByInvoiceDateBetween(startLastMonth, endLastMonth);
        long   totalInvoices    = scoped ? invoiceRepository.countByDealer(dealerId)                                                  : invoiceRepository.count();
        double allTimeSum       = scoped ? invoiceRepository.sumGrandTotalAllByDealer(dealerId)                                       : invoiceRepository.sumGrandTotalAll();
        double avgInvoice       = totalInvoices > 0 ? allTimeSum / totalInvoices : 0.0;

        double totalSalaryPaid    = scoped ? salaryRepository.sumTotalPaidSalariesByDealer(dealerId)                            : salaryRepository.sumTotalPaidSalaries();
        double salaryThisMonth    = scoped ? salaryRepository.sumPaidSalariesForMonthByDealer(today.getYear(), today.getMonthValue(), dealerId) : salaryRepository.sumPaidSalariesForMonth(today.getYear(), today.getMonthValue());
        double netProfitThisMonth = revenueThisMonth - salaryThisMonth;

        return new AuditSummaryResponse(
                open, inProgress, ready, delivered, closed, total,
                labourTotal, partsTotal, ancTotal, totalRevenue,
                revenueThisMonth, invThisMonth,
                revenuePrevMonth, invPrevMonth,
                totalInvoices, avgInvoice,
                totalSalaryPaid, salaryThisMonth, netProfitThisMonth
        );
    }
}
