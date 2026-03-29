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

    @Column(nullable = false)
    private String partNumber;

    @Column(nullable = false)
    private String description;

    private String partType = "OEM";  // OEM, AM, DEALER_SUPPLY

    private int quantity = 1;
    private double unitPrice;
    private double totalPrice;
}
