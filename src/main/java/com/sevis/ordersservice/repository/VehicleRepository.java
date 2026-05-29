package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByRegNumber(String regNumber);

    @Query("SELECT DISTINCT v FROM Vehicle v WHERE v.id IN " +
           "(SELECT jc.vehicle.id FROM JobCard jc WHERE jc.dealerId = :dealerId AND jc.vehicle IS NOT NULL)")
    List<Vehicle> findByDealerId(@Param("dealerId") Long dealerId);
}
