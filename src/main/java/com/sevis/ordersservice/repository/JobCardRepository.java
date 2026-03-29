package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.JobCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface JobCardRepository extends JpaRepository<JobCard, Long> {
    List<JobCard> findByDealerIdOrderByCreatedAtDesc(Long dealerId);
    List<JobCard> findAllByOrderByCreatedAtDesc();
    @Query("SELECT COUNT(jc) FROM JobCard jc WHERE jc.dateIn = :date")
    long countByDate(@org.springframework.data.repository.query.Param("date") LocalDate date);

    @Query("SELECT jc FROM JobCard jc LEFT JOIN FETCH jc.customer LEFT JOIN FETCH jc.vehicle " +
           "LEFT JOIN FETCH jc.labourItems LEFT JOIN FETCH jc.parts " +
           "LEFT JOIN FETCH jc.ancillaryItems WHERE jc.id = :id")
    java.util.Optional<JobCard> findByIdWithDetails(Long id);
}
