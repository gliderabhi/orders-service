package com.sevis.ordersservice.controller;

import com.sevis.ordersservice.model.DealerTechnicianAssignment;
import com.sevis.ordersservice.model.Technician;
import com.sevis.ordersservice.repository.DealerTechnicianAssignmentRepository;
import com.sevis.ordersservice.repository.TechnicianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Technician roster (name/employee code/specialisation/active-assignment) is
// slow-changing staff data, not a live customer-facing status field, so a
// longer TTL (2min, see CacheConfig) is fine. Every write below evicts the
// whole cache since these list reads are parameterized by role/dealer and
// precise per-key eviction isn't worth the complexity here (correctness
// over hit-rate, per policy).
@RestController
@RequestMapping("/api/technicians")
@RequiredArgsConstructor
public class TechnicianController {

    private final TechnicianRepository technicianRepository;
    private final DealerTechnicianAssignmentRepository assignmentRepository;

    record TechnicianResponse(
            Long id, Long userId, String name, String phone, String employeeCode,
            String panNumber, String aadhaarNumber,
            String specialisation, boolean active, Long dealerId,
            String joinedDate, String leftDate
    ) {}

    record TechnicianRequest(
            String name, String phone, String employeeCode,
            String specialisation, Long dealerId,
            String panNumber, String aadhaarNumber,
            Long userId
    ) {}

    private TechnicianResponse toResponse(DealerTechnicianAssignment a) {
        Technician t = a.getTechnician();
        return new TechnicianResponse(
                t.getId(), t.getUserId(), t.getName(), t.getPhone(), t.getEmployeeCode(),
                t.getPanNumber(), t.getAadhaarNumber(),
                a.getSpecialisation(), a.isActive(), a.getDealerId(),
                a.getJoinedDate() != null ? a.getJoinedDate().toString() : null,
                a.getLeftDate() != null ? a.getLeftDate().toString() : null
        );
    }

    @Cacheable(value = "technicianList", key = "'all-' + #userId + '-' + #userRole", sync = true)
    @GetMapping
    public List<TechnicianResponse> getAll(
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        boolean isAdmin = "ADMIN".equals(userRole);
        List<DealerTechnicianAssignment> assignments = isAdmin
                ? assignmentRepository.findAll()
                : assignmentRepository.findByDealerId(userId);
        return assignments.stream().map(this::toResponse).toList();
    }

    @Cacheable(value = "technicianList", key = "'active-' + #userId + '-' + #userRole", sync = true)
    @GetMapping("/active")
    public List<TechnicianResponse> getActive(
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        boolean isAdmin = "ADMIN".equals(userRole);
        List<DealerTechnicianAssignment> assignments = isAdmin
                ? assignmentRepository.findByActiveTrue()
                : assignmentRepository.findByDealerIdAndActiveTrue(userId);
        return assignments.stream().map(this::toResponse).toList();
    }

    /**
     * Create technician + initial dealer assignment.
     * Dedup logic: if name + PAN matches OR name + Aadhaar matches an existing
     * technician, reuse that record and open a new assignment instead of duplicating.
     */
    @CacheEvict(value = "technicianList", allEntries = true)
    @PostMapping
    public ResponseEntity<TechnicianResponse> create(
            @RequestBody TechnicianRequest req,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long callerId) {
        if ("TECHNICIAN".equals(userRole)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Technician t = resolveOrCreate(req);

        assignmentRepository.findActiveByTechnicianId(t.getId()).ifPresent(existing -> {
            existing.setActive(false);
            existing.setLeftDate(LocalDate.now());
            assignmentRepository.save(existing);
        });

        Long dealerId = req.dealerId() != null ? req.dealerId() : ("ADMIN".equals(userRole) ? null : callerId);

        DealerTechnicianAssignment a = new DealerTechnicianAssignment();
        a.setTechnician(t);
        a.setSpecialisation(req.specialisation());
        a.setDealerId(dealerId);
        a.setActive(true);
        a.setJoinedDate(LocalDate.now());
        assignmentRepository.save(a);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(a));
    }

    private Technician resolveOrCreate(TechnicianRequest req) {
        String name = req.name().trim();
        String pan  = hasPan(req)  ? req.panNumber().toUpperCase().trim()  : null;
        String aadh = hasAadh(req) ? req.aadhaarNumber().trim()            : null;

        // Try name + PAN
        if (pan != null) {
            Optional<Technician> match = technicianRepository.findByNameIgnoreCaseAndPanNumber(name, pan);
            if (match.isPresent()) {
                return syncContactDetails(match.get(), req);
            }
        }

        // Try name + Aadhaar
        if (aadh != null) {
            Optional<Technician> match = technicianRepository.findByNameIgnoreCaseAndAadhaarNumber(name, aadh);
            if (match.isPresent()) {
                return syncContactDetails(match.get(), req);
            }
        }

        // No match — create new person record
        Technician t = new Technician();
        t.setName(name);
        t.setPhone(req.phone());
        t.setEmployeeCode(req.employeeCode());
        if (pan  != null) t.setPanNumber(pan);
        if (aadh != null) t.setAadhaarNumber(aadh);
        if (req.userId() != null) t.setUserId(req.userId());
        return technicianRepository.save(t);
    }

    private Technician syncContactDetails(Technician t, TechnicianRequest req) {
        t.setPhone(req.phone());
        t.setEmployeeCode(req.employeeCode());
        if (hasPan(req)  && t.getPanNumber()     == null) t.setPanNumber(req.panNumber().toUpperCase().trim());
        if (hasAadh(req) && t.getAadhaarNumber() == null) t.setAadhaarNumber(req.aadhaarNumber().trim());
        if (req.userId() != null) t.setUserId(req.userId());
        return technicianRepository.save(t);
    }

    private boolean hasPan(TechnicianRequest req)  { return req.panNumber()     != null && !req.panNumber().isBlank(); }
    private boolean hasAadh(TechnicianRequest req) { return req.aadhaarNumber() != null && !req.aadhaarNumber().isBlank(); }

    @CacheEvict(value = "technicianList", allEntries = true)
    @PutMapping("/{id}")
    public ResponseEntity<TechnicianResponse> update(
            @PathVariable Long id,
            @RequestBody TechnicianRequest req,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        if ("TECHNICIAN".equals(userRole)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Technician t = technicianRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));
        t.setName(req.name());
        t.setPhone(req.phone());
        t.setEmployeeCode(req.employeeCode());
        if (hasPan(req))  t.setPanNumber(req.panNumber().toUpperCase().trim());
        if (hasAadh(req)) t.setAadhaarNumber(req.aadhaarNumber().trim());
        if (req.userId() != null) t.setUserId(req.userId());
        technicianRepository.save(t);

        DealerTechnicianAssignment a = assignmentRepository.findActiveByTechnicianId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active assignment found"));
        a.setSpecialisation(req.specialisation());
        a.setDealerId(req.dealerId());
        assignmentRepository.save(a);

        return ResponseEntity.ok(toResponse(a));
    }

    @CacheEvict(value = "technicianList", allEntries = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        if ("TECHNICIAN".equals(userRole)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        DealerTechnicianAssignment a = assignmentRepository.findActiveByTechnicianId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active assignment found"));
        a.setActive(false);
        a.setLeftDate(LocalDate.now());
        assignmentRepository.save(a);
        return ResponseEntity.noContent().build();
    }

    @CacheEvict(value = "technicianList", allEntries = true)
    @PostMapping("/{id}/reassign")
    public ResponseEntity<TechnicianResponse> reassign(
            @PathVariable Long id,
            @RequestBody Map<String, Object> req,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        if ("TECHNICIAN".equals(userRole)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Technician t = technicianRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));

        assignmentRepository.findActiveByTechnicianId(id).ifPresent(existing -> {
            existing.setActive(false);
            existing.setLeftDate(LocalDate.now());
            assignmentRepository.save(existing);
        });

        DealerTechnicianAssignment a = new DealerTechnicianAssignment();
        a.setTechnician(t);
        a.setSpecialisation((String) req.get("specialisation"));
        Object dealerIdObj = req.get("dealerId");
        a.setDealerId(dealerIdObj != null ? Long.parseLong(dealerIdObj.toString()) : null);
        a.setActive(true);
        a.setJoinedDate(LocalDate.now());
        assignmentRepository.save(a);

        return ResponseEntity.ok(toResponse(a));
    }
}
