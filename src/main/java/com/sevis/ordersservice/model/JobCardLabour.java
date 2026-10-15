package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job_card_labour")
@Getter
@Setter
public class JobCardLabour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    @Column(nullable = false)
    private String description;

    private String type = "LABOUR";  // LABOUR, INSPECTION, SUBLET

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    private Technician technician;

    private double quantity = 1.0;
    private double rate;
    private double amount;

    private String jobCode;
    private String hsnCode = "998714";
    private Double cgstRate;
    private Double sgstRate;
}
