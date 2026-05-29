package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "technician_salaries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"technician_id", "salary_month", "salary_year"}))
@Getter
@Setter
public class TechnicianSalary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private Technician technician;

    @Column(name = "salary_month", nullable = false)
    private int month; // 1-12

    @Column(name = "salary_year", nullable = false)
    private int year;

    private double baseSalary;
    private double bonus;
    private double deductions;
    private double netPay; // computed: baseSalary + bonus - deductions

    private String status = "PENDING"; // PENDING, PAID

    private LocalDate paidDate;

    private String notes;
}
