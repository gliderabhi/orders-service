package com.sevis.ordersservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String regNumber;

    private String make;

    private String model;

    private String variant;
    private Integer year;
    private String chassisNo;
    private String engineNo;
    private String color;
    private String fuelType;  // PETROL, DIESEL, CNG, ELECTRIC, HYBRID

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
}
