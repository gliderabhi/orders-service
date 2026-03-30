package com.sevis.ordersservice.dto.response;

import com.sevis.ordersservice.model.Invoice;
import com.sevis.ordersservice.model.InvoiceLineItem;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class InvoiceDetailResponse {

    private final Long id;
    private final String invoiceNumber;
    private final String invoiceDate;
    private final String originalJobCardNumber;
    private final String jobCardDate;
    private final Long jobCardId;
    private final String dealerGstin;
    private final String dealerPan;
    private final String dealerName;
    private final String serviceType;
    private final String paymentMethod;
    private final String vehicleRegNo;
    private final String chassisNo;
    private final Integer kms;
    private final String placeOfSupply;
    private final String preparedBy;
    private final Double partsNetTaxableAmount;
    private final Double totalTaxAmount;
    private final Double grandTotal;
    private final Double adjustments;
    private final List<LineItemInfo> lineItems;

    public InvoiceDetailResponse(Invoice inv) {
        this.id                      = inv.getId();
        this.invoiceNumber           = inv.getInvoiceNumber();
        this.invoiceDate             = inv.getInvoiceDate()    != null ? inv.getInvoiceDate().toString()    : null;
        this.originalJobCardNumber   = inv.getOriginalJobCardNumber();
        this.jobCardDate             = inv.getJobCardDate()    != null ? inv.getJobCardDate().toString()    : null;
        this.jobCardId               = inv.getJobCard()        != null ? inv.getJobCard().getId()           : null;
        this.dealerGstin             = inv.getDealerGstin();
        this.dealerPan               = inv.getDealerPan();
        this.dealerName              = inv.getDealerName();
        this.serviceType             = inv.getServiceType();
        this.paymentMethod           = inv.getPaymentMethod();
        this.vehicleRegNo            = inv.getVehicleRegNo();
        this.chassisNo               = inv.getChassisNo();
        this.kms                     = inv.getKms();
        this.placeOfSupply           = inv.getPlaceOfSupply();
        this.preparedBy              = inv.getPreparedBy();
        this.partsNetTaxableAmount   = inv.getPartsNetTaxableAmount();
        this.totalTaxAmount          = inv.getTotalTaxAmount();
        this.grandTotal              = inv.getGrandTotal();
        this.adjustments             = inv.getAdjustments();
        this.lineItems               = inv.getLineItems().stream().map(LineItemInfo::new).collect(Collectors.toList());
    }

    @Getter
    public static class LineItemInfo {
        private final Long id;
        private final Integer lineNumber;
        private final String hsnCode;
        private final String partNumber;
        private final String description;
        private final String type;
        private final Double quantity;
        private final Double rate;
        private final Double baseAmount;
        private final Double discountAmount;
        private final Double taxableAmount;
        private final Double cgstRate;
        private final Double cgstAmount;
        private final Double sgstRate;
        private final Double sgstAmount;
        private final Double totalAmount;

        public LineItemInfo(InvoiceLineItem li) {
            this.id             = li.getId();
            this.lineNumber     = li.getLineNumber();
            this.hsnCode        = li.getHsnCode();
            this.partNumber     = li.getPartNumber();
            this.description    = li.getDescription();
            this.type           = li.getType();
            this.quantity       = li.getQuantity();
            this.rate           = li.getRate();
            this.baseAmount     = li.getBaseAmount();
            this.discountAmount = li.getDiscountAmount();
            this.taxableAmount  = li.getTaxableAmount();
            this.cgstRate       = li.getCgstRate();
            this.cgstAmount     = li.getCgstAmount();
            this.sgstRate       = li.getSgstRate();
            this.sgstAmount     = li.getSgstAmount();
            this.totalAmount    = li.getTotalAmount();
        }
    }
}
