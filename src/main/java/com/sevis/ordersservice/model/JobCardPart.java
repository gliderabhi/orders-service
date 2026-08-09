package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job_card_parts")
@Getter
@Setter
public class JobCardPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    // Every part belongs to a labour job (e.g. engine oil under "Oil change") — parts are
    // never added standalone. Nullable during the migration window only, so existing rows
    // can be backfilled before this is tightened to NOT NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "labour_id")
    private JobCardLabour labour;

    @Column(nullable = false)
    private String partNumber;

    @Column(nullable = false)
    private String description;

    private String partType = "OEM";  // OEM, AM, DEALER_SUPPLY

    private int quantity = 1;
    private double unitPrice;
    private double totalPrice;
}
