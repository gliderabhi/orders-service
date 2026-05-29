package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.DealerTechnicianAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DealerTechnicianAssignmentRepository extends JpaRepository<DealerTechnicianAssignment, Long> {

    List<DealerTechnicianAssignment> findByActiveTrue();

    List<DealerTechnicianAssignment> findByDealerIdAndActiveTrue(Long dealerId);

    List<DealerTechnicianAssignment> findByDealerId(Long dealerId);

    @Query("SELECT a FROM DealerTechnicianAssignment a WHERE a.technician.id = :technicianId AND a.active = true")
    Optional<DealerTechnicianAssignment> findActiveByTechnicianId(@Param("technicianId") Long technicianId);
}
