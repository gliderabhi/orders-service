package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job_card_checks")
@Getter
@Setter
public class JobCardChecks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    private int fuelLevel = 0;      // 0-100 percentage

    private String tireFLPsi;
    private String tireRLPsi;
    private String tireFRPsi;
    private String tireRRPsi;
    private String tireSparePsi;

    private boolean hasToolKit;
    private boolean hasStepney;
    private boolean hasBrochure;
    private boolean hasInsurance;
    private boolean hasPUC;
    private boolean hasRC;

    @Column(length = 500)
    private String notes;
}
