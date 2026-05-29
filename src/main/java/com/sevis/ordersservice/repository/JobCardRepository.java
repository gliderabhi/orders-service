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

    @Query("SELECT jc FROM JobCard jc LEFT JOIN FETCH jc.customer LEFT JOIN FETCH jc.vehicle " +
           "WHERE (:from IS NULL OR jc.dateIn >= :from) AND (:to IS NULL OR jc.dateIn <= :to) " +
           "ORDER BY jc.dateIn DESC")
    List<JobCard> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT jc FROM JobCard jc LEFT JOIN FETCH jc.customer LEFT JOIN FETCH jc.vehicle " +
           "WHERE jc.dealerId = :dealerId " +
           "AND (:from IS NULL OR jc.dateIn >= :from) AND (:to IS NULL OR jc.dateIn <= :to) " +
           "ORDER BY jc.dateIn DESC")
    List<JobCard> findByDealerIdAndDateRange(@Param("dealerId") Long dealerId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);


    @Query("SELECT jc FROM JobCard jc LEFT JOIN FETCH jc.customer LEFT JOIN FETCH jc.vehicle " +
           "WHERE jc.vehicle.id = :vehicleId ORDER BY jc.dateIn DESC")
    List<JobCard> findByVehicleId(@Param("vehicleId") Long vehicleId);

    @Query("SELECT COUNT(jc) FROM JobCard jc WHERE jc.dateIn = :date")
    long countByDate(@Param("date") LocalDate date);

    @Query("SELECT COUNT(jc) FROM JobCard jc WHERE jc.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(jc) FROM JobCard jc WHERE jc.status = :status AND jc.dealerId = :dealerId")
    long countByStatusAndDealerId(@Param("status") String status, @Param("dealerId") Long dealerId);

    @Query("SELECT COALESCE(SUM(b.labourTotal), 0) FROM JobCardBilling b")
    double sumLabourTotal();

    @Query("SELECT COALESCE(SUM(b.labourTotal), 0) FROM JobCardBilling b WHERE b.jobCard.dealerId = :dealerId")
    double sumLabourTotalByDealer(@Param("dealerId") Long dealerId);

    @Query("SELECT COALESCE(SUM(b.partsTotal), 0) FROM JobCardBilling b")
    double sumPartsTotal();

    @Query("SELECT COALESCE(SUM(b.partsTotal), 0) FROM JobCardBilling b WHERE b.jobCard.dealerId = :dealerId")
    double sumPartsTotalByDealer(@Param("dealerId") Long dealerId);

    @Query("SELECT COALESCE(SUM(b.ancillaryTotal), 0) FROM JobCardBilling b")
    double sumAncillaryTotal();

    @Query("SELECT COALESCE(SUM(b.ancillaryTotal), 0) FROM JobCardBilling b WHERE b.jobCard.dealerId = :dealerId")
    double sumAncillaryTotalByDealer(@Param("dealerId") Long dealerId);

    @Query("SELECT COALESCE(SUM(b.grandTotal), 0) FROM JobCardBilling b WHERE b.jobCard.status IN ('DELIVERED', 'CLOSED')")
    double sumGrandTotalDeliveredClosed();

    @Query("SELECT COALESCE(SUM(b.grandTotal), 0) FROM JobCardBilling b WHERE b.jobCard.status IN ('DELIVERED', 'CLOSED') AND b.jobCard.dealerId = :dealerId")
    double sumGrandTotalDeliveredClosedByDealer(@Param("dealerId") Long dealerId);
}
