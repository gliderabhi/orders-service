package com.sevis.ordersservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuditSummaryResponse {
    private final long   openJobCards;
    private final long   inProgressJobCards;
    private final long   readyJobCards;
    private final long   deliveredJobCards;
    private final long   closedJobCards;
    private final long   totalJobCards;
    private final double labourChargesTotal;
    private final double partsRevenueTotal;
    private final double ancillaryRevenueTotal;
    private final double totalRevenue;
    private final double revenueThisMonth;
    private final long   invoicesThisMonth;
    private final double revenuePreviousMonth;
    private final long   invoicesPreviousMonth;
    private final long   totalInvoices;
    private final double averageInvoiceValue;
}
