package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_line_items")
@Getter
@Setter
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    private Integer lineNumber;
    private String hsnCode;
    private String partNumber;

    @Column(length = 500)
    private String description;

    private String type;   // FREESERVICE, LABOUR, PARTS, REPLACEMENT, INSPECTION
    private String uom;
    private Double quantity;
    private Double rate;
    private Double baseAmount;
    private Double discountAmount;
    private Double taxableAmount;
    private Double cgstRate;
    private Double cgstAmount;
    private Double sgstRate;
    private Double sgstAmount;
    private Double totalAmount;
}
