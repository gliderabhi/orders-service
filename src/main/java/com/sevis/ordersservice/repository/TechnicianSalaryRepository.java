package com.sevis.ordersservice.repository;

import com.sevis.ordersservice.model.TechnicianSalary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TechnicianSalaryRepository extends JpaRepository<TechnicianSalary, Long> {

    List<TechnicianSalary> findByTechnicianIdOrderByYearDescMonthDesc(Long technicianId);

    List<TechnicianSalary> findByMonthAndYearOrderByTechnicianId(int month, int year);

    Optional<TechnicianSalary> findByTechnicianIdAndMonthAndYear(Long technicianId, int month, int year);

    @Query("SELECT COALESCE(SUM(s.netPay), 0) FROM TechnicianSalary s WHERE s.status = 'PAID'")
    double sumTotalPaidSalaries();

    @Query("SELECT COALESCE(SUM(s.netPay), 0) FROM TechnicianSalary s WHERE s.status = 'PAID' AND s.year = :year AND s.month = :month")
    double sumPaidSalariesForMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(s.netPay), 0) FROM TechnicianSalary s WHERE s.status = 'PAID' " +
           "AND s.technician.id IN (SELECT a.technician.id FROM DealerTechnicianAssignment a WHERE a.dealerId = :dealerId)")
    double sumTotalPaidSalariesByDealer(@Param("dealerId") Long dealerId);

    @Query("SELECT COALESCE(SUM(s.netPay), 0) FROM TechnicianSalary s WHERE s.status = 'PAID' AND s.year = :year AND s.month = :month " +
           "AND s.technician.id IN (SELECT a.technician.id FROM DealerTechnicianAssignment a WHERE a.dealerId = :dealerId)")
    double sumPaidSalariesForMonthByDealer(@Param("year") int year, @Param("month") int month, @Param("dealerId") Long dealerId);
}
