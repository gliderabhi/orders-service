package com.sevis.ordersservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String phone;

    private String email;

    @Column(length = 500)
    private String address;

    // ── Loyalty ───────────────────────────────────────────────────────────────
    private String loyaltyMemberNumber;    // e.g. MBR-000042
    private Integer loyaltyPoints = 0;     // cumulative point balance
    private String loyaltyTier = "MEMBER"; // MEMBER / SILVER / GOLD / PLATINUM

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
