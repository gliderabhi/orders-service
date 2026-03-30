package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_cards")
@Getter
@Setter
public class JobCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String jobCardNumber;

    private Long dealerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(nullable = false)
    private String serviceType;   // PERIODIC_SERVICE, RUNNING_REPAIR, BODYWORK, INSPECTION, ACCIDENTAL, WARRANTY

    @Column(nullable = false)
    private String status = "OPEN";  // OPEN, IN_PROGRESS, READY, DELIVERED, CLOSED

    private int kmIn;

    private LocalDate expectedDelivery;
    private LocalDate dateIn;
    private LocalDate dateOut;

    @Column(length = 1000)
    private String customerComplaint;

    @Column(length = 1000)
    private String technicianRemarks;

    private String advisorName;

    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobCardLabour> labourItems = new ArrayList<>();

    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobCardPart> parts = new ArrayList<>();

    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobCardAncillary> ancillaryItems = new ArrayList<>();

    @OneToOne(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private JobCardChecks checks;

    @OneToOne(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private JobCardBilling billing;

    // Original job card number from external DMS/PDF (e.g. JC-JhaDam-GP1-2526-000212)
    private String externalJobCardNumber;

    @OneToMany(mappedBy = "jobCard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Invoice> invoices = new ArrayList<>();

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.dateIn == null) this.dateIn = LocalDate.now();
    }
}
