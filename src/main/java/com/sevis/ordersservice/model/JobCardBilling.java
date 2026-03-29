package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job_card_billing")
@Getter
@Setter
public class JobCardBilling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    private double labourTotal;
    private double partsTotal;
    private double ancillaryTotal;
    private double subTotal;
    private double discount;
    private double taxableAmount;

    private double cgstRate;
    private double cgstAmount;
    private double sgstRate;
    private double sgstAmount;
    private double igstRate;
    private double igstAmount;

    private double grandTotal;
    private double advanceAmount;
    private double balanceDue;

    private String paymentType = "CASH";  // CASH, CARD, ONLINE, CREDIT
}
