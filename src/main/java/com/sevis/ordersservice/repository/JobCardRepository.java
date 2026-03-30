package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.JobCard;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobCardRepository extends JpaRepository<JobCard, Long> {

    @Query("SELECT jc FROM JobCard jc WHERE jc.externalJobCardNumber = :extNo")
    Optional<JobCard> findByExternalJobCardNumber(@Param("extNo") String extNo);

    // JOIN FETCH on ManyToOne (customer/vehicle) is safe — no bag issue
    @Query("SELECT jc FROM JobCard jc LEFT JOIN FETCH jc.customer LEFT JOIN FETCH jc.vehicle " +
           "WHERE jc.dealerId = :dealerId ORDER BY jc.createdAt DESC")
    List<JobCard> findByDealerIdWithSummary(@Param("dealerId") Long dealerId);

    @EntityGraph(attributePaths = {"customer", "vehicle"})
    List<JobCard> findAllByOrderByCreatedAtDesc();


    @Query("SELECT COUNT(jc) FROM JobCard jc WHERE jc.dateIn = :date")
    long countByDate(@Param("date") LocalDate date);
}
