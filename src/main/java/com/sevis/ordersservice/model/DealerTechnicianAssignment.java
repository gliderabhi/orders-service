package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "dealer_technician_assignments")
@Getter
@Setter
public class DealerTechnicianAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private Technician technician;

    // nullable — reserved for multi-dealer support
    private Long dealerId;

    private String specialisation; // MECHANICAL, ELECTRICAL, BODYWORK, AC, GENERAL

    private boolean active = true;

    private LocalDate joinedDate;

    private LocalDate leftDate;
}
