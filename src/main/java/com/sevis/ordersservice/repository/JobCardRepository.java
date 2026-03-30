package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.JobCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JobCardRepository extends JpaRepository<JobCard, Long> {

    // JOIN FETCH on ManyToOne (customer/vehicle) is safe — no bag issue
    @Query("SELECT jc FROM JobCard jc LEFT JOIN FETCH jc.customer LEFT JOIN FETCH jc.vehicle " +
           "WHERE jc.dealerId = :dealerId ORDER BY jc.createdAt DESC")
    List<JobCard> findByDealerIdWithSummary(@Param("dealerId") Long dealerId);

    @Query("SELECT jc FROM JobCard jc LEFT JOIN FETCH jc.customer LEFT JOIN FETCH jc.vehicle " +
           "ORDER BY jc.createdAt DESC")
    List<JobCard> findAllWithSummary();

    @Query("SELECT COUNT(jc) FROM JobCard jc WHERE jc.dateIn = :date")
    long countByDate(@Param("date") LocalDate date);
}
