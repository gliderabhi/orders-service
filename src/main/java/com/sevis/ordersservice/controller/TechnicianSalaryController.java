package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.model.Technician;
import com.sevis.ordersservice.model.TechnicianSalary;
import com.sevis.ordersservice.repository.TechnicianRepository;
import com.sevis.ordersservice.repository.TechnicianSalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

// Salary records are financial/payroll data, infrequently read and not a
// live customer-facing status — a medium TTL (2min, see CacheConfig) is
// fine. Both caches are list-shaped (by technician, by month/year) so every
// write evicts allEntries on both rather than computing precise keys.
@RestController
@RequestMapping("/api/technician-salaries")
@RequiredArgsConstructor
public class TechnicianSalaryController {

    private final TechnicianSalaryRepository salaryRepository;
    private final TechnicianRepository technicianRepository;

    record SalaryRequest(
            Long technicianId, int month, int year,
            double baseSalary, double bonus, double deductions,
            String notes
    ) {}

    record SalaryResponse(
            Long id, Long technicianId, String technicianName,
            int month, int year,
            double baseSalary, double bonus, double deductions, double netPay,
            String status, String paidDate, String notes
    ) {}

    private SalaryResponse toResponse(TechnicianSalary s) {
        return new SalaryResponse(
                s.getId(), s.getTechnician().getId(), s.getTechnician().getName(),
                s.getMonth(), s.getYear(),
                s.getBaseSalary(), s.getBonus(), s.getDeductions(), s.getNetPay(),
                s.getStatus(),
                s.getPaidDate() != null ? s.getPaidDate().toString() : null,
                s.getNotes()
        );
    }

    /** All salary records for a technician */
    @Cacheable(value = "salaryByTechnician", key = "#technicianId", sync = true)
    @GetMapping("/technician/{technicianId}")
    public List<SalaryResponse> getByTechnician(@PathVariable Long technicianId) {
        return salaryRepository.findByTechnicianIdOrderByYearDescMonthDesc(technicianId)
                .stream().map(this::toResponse).toList();
    }

    /** All salary records for a given month/year (payroll view) */
    @Cacheable(value = "salaryByMonthYear", key = "#month + '-' + #year", sync = true)
    @GetMapping
    public List<SalaryResponse> getByMonthYear(
            @RequestParam int month, @RequestParam int year) {
        return salaryRepository.findByMonthAndYearOrderByTechnicianId(month, year)
                .stream().map(this::toResponse).toList();
    }

    /** Create or update salary record for a technician+month */
    @Caching(evict = {
            @CacheEvict(value = "salaryByTechnician", allEntries = true),
            @CacheEvict(value = "salaryByMonthYear", allEntries = true)
    })
    @PostMapping
    public ResponseEntity<SalaryResponse> upsert(
            @RequestBody SalaryRequest req,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        if ("TECHNICIAN".equals(userRole)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Technician t = technicianRepository.findById(req.technicianId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));

        TechnicianSalary s = salaryRepository
                .findByTechnicianIdAndMonthAndYear(req.technicianId(), req.month(), req.year())
                .orElseGet(TechnicianSalary::new);

        s.setTechnician(t);
        s.setMonth(req.month());
        s.setYear(req.year());
        s.setBaseSalary(req.baseSalary());
        s.setBonus(req.bonus());
        s.setDeductions(req.deductions());
        s.setNetPay(req.baseSalary() + req.bonus() - req.deductions());
        s.setNotes(req.notes());
        if (s.getStatus() == null) s.setStatus("PENDING");

        salaryRepository.save(s);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(s));
    }

    /** Mark salary as paid */
    @Caching(evict = {
            @CacheEvict(value = "salaryByTechnician", allEntries = true),
            @CacheEvict(value = "salaryByMonthYear", allEntries = true)
    })
    @PatchMapping("/{id}/pay")
    public ResponseEntity<SalaryResponse> markPaid(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        if ("TECHNICIAN".equals(userRole)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        TechnicianSalary s = salaryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salary record not found"));
        s.setStatus("PAID");
        s.setPaidDate(LocalDate.now());
        salaryRepository.save(s);
        return ResponseEntity.ok(toResponse(s));
    }

    @Caching(evict = {
            @CacheEvict(value = "salaryByTechnician", allEntries = true),
            @CacheEvict(value = "salaryByMonthYear", allEntries = true)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        if ("TECHNICIAN".equals(userRole)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        salaryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
