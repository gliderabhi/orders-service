package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job_card_ancillary")
@Getter
@Setter
public class JobCardAncillary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_card_id", nullable = false)
    private JobCard jobCard;

    @Column(nullable = false)
    private String description;

    private double amount;
}
