package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "technicians")
@Getter
@Setter
public class Technician {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Links to the user-service user account for this technician's login
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String phone;

    private String employeeCode;

    // Default labour rate for this technician — pre-fills the Rate field when they're
    // assigned to a labour item on a job card, set when the technician is hired/created.
    // Defaults to 100 for staff hired without an explicit rate (e.g. helpers).
    private double baseRate = 100;

    @Column(unique = true)
    private String panNumber;

    @Column(unique = true)
    private String aadhaarNumber;
}
