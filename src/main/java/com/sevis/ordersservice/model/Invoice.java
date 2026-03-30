package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String invoiceNumber;

    private LocalDate invoiceDate;

    // ManyToOne keeps it flexible — multiple invoices per job card possible in future
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_card_id")
    private JobCard jobCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private Long dealerId;
    private String dealerGstin;
    private String dealerPan;
    private String dealerName;

    private String serviceType;
    private String paymentMethod;
    private String originalJobCardNumber;   // job card number from the PDF/DMS
    private LocalDate jobCardDate;
    private String vehicleRegNo;
    private String chassisNo;
    private Integer kms;
    private String placeOfSupply;
    private String preparedBy;

    private Double partsNetTaxableAmount;
    private Double labourTaxableAmount;
    private Double totalTaxAmount;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double igstAmount;
    private Double grandTotal;
    private Double adjustments;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
