package com.sevis.ordersservice.service;

import com.sevis.ordersservice.dto.request.AncillaryItemRequest;
import com.sevis.ordersservice.dto.request.CreateJobCardRequest;
import com.sevis.ordersservice.dto.request.LabourItemRequest;
import com.sevis.ordersservice.dto.request.PartItemRequest;
import com.sevis.ordersservice.dto.response.JobCardDetailResponse;
import com.sevis.ordersservice.dto.response.JobCardSummaryResponse;
import com.sevis.ordersservice.model.*;
import com.sevis.ordersservice.repository.CustomerRepository;
import com.sevis.ordersservice.repository.JobCardRepository;
import com.sevis.ordersservice.repository.TechnicianRepository;
import com.sevis.ordersservice.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Job card status (and the billing totals attached to it) is a live field
// dealers/customers/technicians expect to see update immediately, so both
// jobCardById and jobCardList carry only a short (20s) TTL — see CacheConfig
// — and every write path below evicts explicitly rather than waiting on it.
@Slf4j
@Service
@RequiredArgsConstructor
public class JobCardService {

    private final JobCardRepository    jobCardRepository;
    private final CustomerRepository   customerRepository;
    private final VehicleRepository    vehicleRepository;
    private final TechnicianRepository technicianRepository;
    private final InvoiceService       invoiceService;
    private final RestTemplate         restTemplate;
    private final CacheManager         cacheManager;

    // vehicleHistory (VehicleController) embeds each job card's status/amount but no
    // write path here evicts it directly — see JobCardService.java history; without
    // this it silently relies on its own 20s TTL, which is fine for most edits but
    // means a technician staring at a vehicle's history right as its status flips can
    // see a stale entry for up to 20s. Status/payment changes are exactly the writes
    // a dealer expects to see reflected immediately, so evict explicitly for those.
    private void evictVehicleHistory(JobCard jc) {
        if (jc.getVehicle() == null) return;
        Cache cache = cacheManager.getCache("vehicleHistory");
        if (cache != null) cache.evict(jc.getVehicle().getId());
    }

    // Inventory (dealer-managed quantities) is the single source of truth for job-card
    // parts — there is no separate "stock" system in this flow.
    private static final String INVENTORY_DEDUCT_URL  = "http://inventory-service/api/inventory/deduct";
    private static final String INVENTORY_RESTORE_URL = "http://inventory-service/api/inventory/restore";

    private static final List<String> VALID_SERVICE_TYPES =
            List.of("PERIODIC_SERVICE", "RUNNING_REPAIR", "BODYWORK", "INSPECTION", "ACCIDENTAL", "WARRANTY");
    // Order matters: this is also the job card's lifecycle sequence. Status can only
    // move forward one stage at a time (no skipping ahead); moving backward is unrestricted.
    private static final List<String> VALID_STATUSES =
            List.of("OPEN", "IN_PROGRESS", "QUALITY_CHECK", "READY", "DELIVERED", "CLOSED");

    // ── List ──────────────────────────────────────────────────────────────────

    // Not cached: this overload is only ever called internally (self-invocation
    // would bypass the Spring cache proxy anyway) — it just delegates to the
    // cached 4-arg overload below.
    @Transactional(readOnly = true)
    public List<JobCardSummaryResponse> getAll(Long dealerId, LocalDate from, LocalDate to) {
        return getAll(dealerId, from, to, null);
    }

    @Cacheable(value = "jobCardList",
            key = "#dealerId + '-' + #from + '-' + #to + '-' + #technicianUserId", sync = true)
    @Transactional(readOnly = true)
    public List<JobCardSummaryResponse> getAll(Long dealerId, LocalDate from, LocalDate to, Long technicianUserId) {
        List<JobCard> cards;
        // TECHNICIAN role: filter to only job cards they have tasks on
        if (technicianUserId != null) {
            cards = jobCardRepository.findByTechnicianUserId(technicianUserId);
            return cards.stream().map(JobCardSummaryResponse::new).collect(Collectors.toList());
        }
        boolean hasDateFilter = from != null || to != null;
        if (hasDateFilter) {
            cards = (dealerId != null)
                    ? jobCardRepository.findByDealerIdAndDateRange(dealerId, from, to)
                    : jobCardRepository.findByDateRange(from, to);
        } else {
            cards = (dealerId != null)
                    ? jobCardRepository.findByDealerIdWithSummary(dealerId)
                    : jobCardRepository.findAllByOrderByCreatedAtDesc();
        }
        return cards.stream().map(JobCardSummaryResponse::new).collect(Collectors.toList());
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    // Cache key includes both dealer and technician scoping so a technician-filtered
    // response never gets served back to the dealer (or another technician) for the same id.
    @Cacheable(value = "jobCardById", key = "#id + '-' + #dealerId + '-' + #technicianUserId", sync = true)
    @Transactional(readOnly = true)
    public JobCardDetailResponse getById(Long id, Long dealerId, Long technicianUserId) {
        JobCard jc = jobCardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job card not found"));
        if (dealerId != null && !jc.getDealerId().equals(dealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        // Force-init each lazy collection separately — avoids MultipleBagFetchException
        jc.getCustomer().getName();
        jc.getVehicle().getRegNumber();
        jc.getLabourItems().size();
        jc.getParts().size();
        jc.getAncillaryItems().size();
        jc.getChecks();
        jc.getBilling();
        jc.getInvoices().size();
        if (technicianUserId != null) {
            boolean assigned = jc.getLabourItems().stream()
                    .anyMatch(l -> l.getTechnician() != null && technicianUserId.equals(l.getTechnician().getUserId()));
            if (!assigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }
        return new JobCardDetailResponse(jc, technicianUserId);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @CacheEvict(value = "jobCardList", allEntries = true)
    @Transactional
    public JobCardDetailResponse create(CreateJobCardRequest req, Long dealerId) {

        // Validate service type
        String st = req.getServiceType().toUpperCase();
        if (!VALID_SERVICE_TYPES.contains(st)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid serviceType. Must be one of: " + VALID_SERVICE_TYPES);
        }

        // 1. Find or create customer
        Customer customer = customerRepository.findByPhone(req.getCustomer().getPhone())
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setPhone(req.getCustomer().getPhone());
                    c.setName(req.getCustomer().getName());
                    c.setEmail(req.getCustomer().getEmail());
                    c.setAddress(req.getCustomer().getAddress());
                    return customerRepository.save(c);
                });
        // Update name/email/address if provided and customer already existed
        if (req.getCustomer().getName() != null && !req.getCustomer().getName().isBlank()) {
            customer.setName(req.getCustomer().getName());
        }

        // 2. Find or create vehicle
        Vehicle vehicle = vehicleRepository.findByRegNumber(req.getVehicle().getRegNumber().toUpperCase())
                .orElseGet(() -> {
                    Vehicle v = new Vehicle();
                    v.setRegNumber(req.getVehicle().getRegNumber().toUpperCase());
                    v.setMake(req.getVehicle().getMake());
                    v.setModel(req.getVehicle().getModel());
                    v.setVariant(req.getVehicle().getVariant());
                    v.setYear(req.getVehicle().getYear());
                    v.setChassisNo(req.getVehicle().getChassisNo());
                    v.setEngineNo(req.getVehicle().getEngineNo());
                    v.setColor(req.getVehicle().getColor());
                    v.setFuelType(req.getVehicle().getFuelType());
                    v.setCustomer(customer);
                    return vehicleRepository.save(v);
                });

        // 3. Build job card
        JobCard jc = new JobCard();
        jc.setJobCardNumber(generateJobCardNumber());
        jc.setDealerId(dealerId);
        jc.setCustomer(customer);
        jc.setVehicle(vehicle);
        jc.setServiceType(st);
        jc.setStatus("OPEN");
        jc.setKmIn(req.getKmIn());
        jc.setCustomerComplaint(req.getCustomerComplaint());
        jc.setAdvisorName(req.getAdvisorName());

        if (req.getExpectedDelivery() != null && !req.getExpectedDelivery().isBlank()) {
            jc.setExpectedDelivery(LocalDate.parse(req.getExpectedDelivery(),
                    DateTimeFormatter.ISO_LOCAL_DATE));
        }

        // 4. Labour items
        double labourTotal = 0;
        for (var item : req.getLabourItems()) {
            JobCardLabour l = new JobCardLabour();
            l.setJobCard(jc);
            l.setDescription(item.getDescription());
            l.setType(item.getType() != null ? item.getType().toUpperCase() : "LABOUR");
            l.setQuantity(item.getQuantity());
            l.setRate(item.getRate());
            double amount = item.getQuantity() * item.getRate();
            l.setAmount(amount);
            labourTotal += amount;
            jc.getLabourItems().add(l);
        }

        // 5. Parts
        double partsTotal = 0;
        for (var part : req.getParts()) {
            JobCardPart p = new JobCardPart();
            p.setJobCard(jc);
            p.setPartNumber(part.getPartNumber());
            p.setDescription(part.getDescription());
            p.setPartType(part.getPartType() != null ? part.getPartType().toUpperCase() : "OEM");
            p.setQuantity(part.getQuantity());
            p.setUnitPrice(part.getUnitPrice());
            double total = part.getQuantity() * part.getUnitPrice();
            p.setTotalPrice(total);
            partsTotal += total;
            jc.getParts().add(p);
        }

        // 6. Ancillary items
        double ancillaryTotal = 0;
        for (var anc : req.getAncillaryItems()) {
            JobCardAncillary a = new JobCardAncillary();
            a.setJobCard(jc);
            a.setDescription(anc.getDescription());
            a.setAmount(anc.getAmount());
            ancillaryTotal += anc.getAmount();
            jc.getAncillaryItems().add(a);
        }

        // 7. Checks
        if (req.getChecks() != null) {
            JobCardChecks checks = new JobCardChecks();
            var cd = req.getChecks();
            checks.setJobCard(jc);
            checks.setFuelLevel(cd.getFuelLevel());
            checks.setTireFLPsi(cd.getTireFLPsi());
            checks.setTireRLPsi(cd.getTireRLPsi());
            checks.setTireFRPsi(cd.getTireFRPsi());
            checks.setTireRRPsi(cd.getTireRRPsi());
            checks.setTireSparePsi(cd.getTireSparePsi());
            checks.setHasToolKit(cd.isHasToolKit());
            checks.setHasStepney(cd.isHasStepney());
            checks.setHasBrochure(cd.isHasBrochure());
            checks.setHasInsurance(cd.isHasInsurance());
            checks.setHasPUC(cd.isHasPUC());
            checks.setHasRC(cd.isHasRC());
            checks.setNotes(cd.getNotes());
            jc.setChecks(checks);
        }

        // 8. Billing
        CreateJobCardRequest.BillingData bd = req.getBilling() != null
                ? req.getBilling()
                : new CreateJobCardRequest.BillingData();

        double subTotal     = labourTotal + partsTotal + ancillaryTotal;
        double taxable      = subTotal - bd.getDiscount();
        double cgstAmt      = taxable * bd.getCgstRate() / 100;
        double sgstAmt      = taxable * bd.getSgstRate() / 100;
        double igstAmt      = taxable * bd.getIgstRate() / 100;
        double grandTotal   = taxable + cgstAmt + sgstAmt + igstAmt;
        double balanceDue   = grandTotal - bd.getAdvanceAmount();

        JobCardBilling billing = new JobCardBilling();
        billing.setJobCard(jc);
        billing.setLabourTotal(labourTotal);
        billing.setPartsTotal(partsTotal);
        billing.setAncillaryTotal(ancillaryTotal);
        billing.setSubTotal(subTotal);
        billing.setDiscount(bd.getDiscount());
        billing.setTaxableAmount(taxable);
        billing.setCgstRate(bd.getCgstRate());
        billing.setCgstAmount(cgstAmt);
        billing.setSgstRate(bd.getSgstRate());
        billing.setSgstAmount(sgstAmt);
        billing.setIgstRate(bd.getIgstRate());
        billing.setIgstAmount(igstAmt);
        billing.setGrandTotal(grandTotal);
        billing.setAdvanceAmount(bd.getAdvanceAmount());
        billing.setBalanceDue(balanceDue);
        billing.setPaymentType(bd.getPaymentType() != null ? bd.getPaymentType() : "CASH");
        jc.setBilling(billing);

        // 9. Save (cascade persists children)
        JobCard saved = jobCardRepository.save(jc);
        return new JobCardDetailResponse(saved);
    }

    // ── Status update ─────────────────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse updateStatus(Long id, String status, Long dealerId) {
        String newStatus = status.toUpperCase();
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status. Must be one of: " + VALID_STATUSES);
        }
        JobCard jc = jobCardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job card not found"));
        if (dealerId != null && !jc.getDealerId().equals(dealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        int currentIdx = VALID_STATUSES.indexOf(jc.getStatus());
        int newIdx     = VALID_STATUSES.indexOf(newStatus);

        // Forward moves must advance exactly one stage at a time; backward moves are unrestricted.
        if (newIdx > currentIdx + 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot skip stages: job card is at " + jc.getStatus() + ", next stage is "
                            + VALID_STATUSES.get(currentIdx + 1));
        }

        if (newIdx > currentIdx) {
            jc.getLabourItems().size(); // init lazy collection
            boolean hasPending = jc.getLabourItems().stream()
                    .anyMatch(l -> l.getTechnician() != null && "PENDING".equals(l.getTaskStatus()));
            if (hasPending) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot advance to " + newStatus + ": some technician tasks are still pending");
            }
        }

        // Vehicle can't leave (DELIVERED) or the job card be archived (CLOSED) until the
        // customer has actually paid — this only applies to forward moves, so reopening a
        // delivered/closed card backward is still unrestricted.
        if (newIdx > currentIdx && ("DELIVERED".equals(newStatus) || "CLOSED".equals(newStatus))) {
            JobCardBilling billing = jc.getBilling();
            if (billing == null || !"PAID".equalsIgnoreCase(billing.getPaymentStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot mark as " + newStatus + ": payment has not been recorded as PAID yet");
            }
        }

        if ("DELIVERED".equals(newStatus) || "CLOSED".equals(newStatus)) {
            jc.setDateOut(LocalDate.now());
        }
        jc.setStatus(newStatus);
        JobCard saved = jobCardRepository.save(jc);
        evictVehicleHistory(saved);
        return new JobCardDetailResponse(saved);
    }

    // ── Update payment ────────────────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse updatePayment(Long id, String paymentType, String paymentStatus, Long dealerId) {
        JobCard jc = jobCardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job card not found"));

        // Payment must be recorded before the job card can move to DELIVERED/CLOSED (enforced
        // in updateStatus), so it needs to be settable any time before that — not gated to
        // CLOSED only, the way it used to be.
        JobCardBilling billing = jc.getBilling();
        if (billing == null) {
            billing = new JobCardBilling();
            billing.setJobCard(jc);
            jc.setBilling(billing);
        }
        billing.setPaymentType(paymentType.toUpperCase());
        billing.setPaymentStatus(paymentStatus.toUpperCase());
        jobCardRepository.save(jc);
        evictVehicleHistory(jc);

        // Auto-generate invoice when marked as PAID
        if ("PAID".equalsIgnoreCase(paymentStatus)) {
            try {
                invoiceService.generateOrUpdateInvoice(id, dealerId != null ? dealerId : 0L);
            } catch (Exception e) {
                log.warn("Invoice generation failed for job card {}: {}", id, e.getMessage());
            }
        }

        // Re-fetch with all collections for response
        jc.getCustomer().getName();
        jc.getVehicle().getRegNumber();
        jc.getLabourItems().size();
        jc.getParts().size();
        jc.getAncillaryItems().size();
        jc.getBilling();
        jc.getInvoices().size();
        return new JobCardDetailResponse(jc);
    }

    // ── Generate PDF bill ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        JobCard jc = jobCardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job card not found"));
        jc.getCustomer().getName();
        jc.getVehicle().getRegNumber();
        jc.getLabourItems().size();
        jc.getParts().size();
        jc.getAncillaryItems().size();
        jc.getBilling();
        try {
            return JobCardPdfGenerator.generate(jc);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate PDF: " + e.getMessage());
        }
    }

    // ── Add / delete labour items ─────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse addLabour(Long id, LabourItemRequest req, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        JobCardLabour l = new JobCardLabour();
        l.setJobCard(jc);
        l.setDescription(req.getDescription());
        l.setType(req.getType() != null ? req.getType().toUpperCase() : "LABOUR");
        if (req.getTechnicianId() != null) {
            technicianRepository.findById(req.getTechnicianId()).ifPresent(l::setTechnician);
        }
        l.setQuantity(req.getQuantity());
        l.setRate(req.getRate());
        l.setAmount(req.getQuantity() * req.getRate());
        jc.getLabourItems().add(l);
        if (req.getParts() != null) {
            for (PartItemRequest partReq : req.getParts()) {
                jc.getParts().add(buildPart(jc, l, partReq));
            }
        }
        // Work has now actually started on the vehicle — OPEN no longer reflects reality.
        if ("OPEN".equals(jc.getStatus())) {
            jc.setStatus("IN_PROGRESS");
        }
        recalcBilling(jc);
        JobCardDetailResponse saved = new JobCardDetailResponse(jobCardRepository.save(jc));
        invoiceService.updateInvoiceIfExists(id, jc.getDealerId());
        return saved;
    }

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse deleteLabour(Long id, Long labourId, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        // Parts only exist attached to a labour item — removing the labour item must also
        // remove (and restock) whatever parts were attached to it.
        List<JobCardPart> attachedParts = jc.getParts().stream()
                .filter(p -> p.getLabour() != null && p.getLabour().getId().equals(labourId))
                .collect(Collectors.toList());
        jc.getParts().removeAll(attachedParts);
        jc.getLabourItems().removeIf(l -> l.getId().equals(labourId));
        recalcBilling(jc);
        JobCardDetailResponse saved = new JobCardDetailResponse(jobCardRepository.save(jc));
        attachedParts.forEach(p -> restoreStock(jc.getDealerId(), p.getPartNumber(), p.getQuantity()));
        invoiceService.updateInvoiceIfExists(id, jc.getDealerId());
        return saved;
    }

    // ── Update task status (technician marks their work done/pending) ──────────

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse updateLabourStatus(Long id, Long labourId, String taskStatus, Long callerUserId) {
        String normalised = taskStatus.toUpperCase();
        if (!"PENDING".equals(normalised) && !"DONE".equals(normalised)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "taskStatus must be PENDING or DONE");
        }
        JobCard jc = jobCardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job card not found"));
        jc.getLabourItems().size();

        JobCardLabour labour = jc.getLabourItems().stream()
                .filter(l -> l.getId().equals(labourId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Labour item not found"));

        // Technicians may only update tasks assigned to them
        if (callerUserId != null && labour.getTechnician() != null) {
            Long techUserId = labour.getTechnician().getUserId();
            if (techUserId != null && !techUserId.equals(callerUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only update tasks assigned to you");
            }
        }

        labour.setTaskStatus(normalised);
        jc.getParts().size();
        jc.getAncillaryItems().size();
        return new JobCardDetailResponse(jobCardRepository.save(jc));
    }

    // ── Add / delete parts ────────────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse addPart(Long id, Long labourId, PartItemRequest req, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        JobCardLabour labour = jc.getLabourItems().stream()
                .filter(l -> l.getId().equals(labourId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Labour item not found"));
        // Deduct first and let it throw on insufficient stock — this is the
        // authoritative check (atomic in inventory-service), not just a UI pre-check.
        jc.getParts().add(buildPart(jc, labour, req));
        recalcBilling(jc);
        JobCardDetailResponse saved = new JobCardDetailResponse(jobCardRepository.save(jc));
        invoiceService.updateInvoiceIfExists(id, jc.getDealerId());
        return saved;
    }

    // Parts always belong to a labour item (e.g. engine oil under "Oil change") — there is
    // no standalone part-adding path.
    private JobCardPart buildPart(JobCard jc, JobCardLabour labour, PartItemRequest req) {
        deductStock(jc.getDealerId(), req.getPartNumber(), req.getQuantity());
        JobCardPart p = new JobCardPart();
        p.setJobCard(jc);
        p.setLabour(labour);
        p.setPartNumber(req.getPartNumber());
        p.setDescription(req.getDescription());
        p.setPartType(req.getPartType() != null ? req.getPartType().toUpperCase() : "OEM");
        p.setQuantity(req.getQuantity());
        p.setUnitPrice(req.getUnitPrice());
        p.setTotalPrice(req.getQuantity() * req.getUnitPrice());
        return p;
    }

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse deletePart(Long id, Long partId, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        Optional<JobCardPart> toDelete = jc.getParts().stream().filter(p -> p.getId().equals(partId)).findFirst();
        jc.getParts().removeIf(p -> p.getId().equals(partId));
        recalcBilling(jc);
        JobCardDetailResponse saved = new JobCardDetailResponse(jobCardRepository.save(jc));
        toDelete.ifPresent(p -> restoreStock(jc.getDealerId(), p.getPartNumber(), p.getQuantity()));
        invoiceService.updateInvoiceIfExists(id, jc.getDealerId());
        return saved;
    }

    // ── Add / delete ancillary items ──────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse addAncillary(Long id, AncillaryItemRequest req, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        JobCardAncillary a = new JobCardAncillary();
        a.setJobCard(jc);
        a.setDescription(req.getDescription());
        a.setAmount(req.getAmount());
        jc.getAncillaryItems().add(a);
        recalcBilling(jc);
        JobCardDetailResponse saved = new JobCardDetailResponse(jobCardRepository.save(jc));
        invoiceService.updateInvoiceIfExists(id, jc.getDealerId());
        return saved;
    }

    @Caching(evict = {
            @CacheEvict(value = "jobCardById", allEntries = true),
            @CacheEvict(value = "jobCardList", allEntries = true)
    })
    @Transactional
    public JobCardDetailResponse deleteAncillary(Long id, Long ancId, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        jc.getAncillaryItems().removeIf(a -> a.getId().equals(ancId));
        recalcBilling(jc);
        JobCardDetailResponse saved = new JobCardDetailResponse(jobCardRepository.save(jc));
        invoiceService.updateInvoiceIfExists(id, jc.getDealerId());
        return saved;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private JobCard getForUpdate(Long id, Long dealerId) {
        JobCard jc = jobCardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job card not found"));
        if (dealerId != null && !jc.getDealerId().equals(dealerId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        jc.getLabourItems().size();
        jc.getParts().size();
        jc.getAncillaryItems().size();
        return jc;
    }

    private void recalcBilling(JobCard jc) {
        double labourTotal = jc.getLabourItems().stream().mapToDouble(JobCardLabour::getAmount).sum();
        double partsTotal  = jc.getParts().stream().mapToDouble(JobCardPart::getTotalPrice).sum();
        double ancTotal    = jc.getAncillaryItems().stream().mapToDouble(JobCardAncillary::getAmount).sum();
        double subTotal    = labourTotal + partsTotal + ancTotal;

        JobCardBilling b = jc.getBilling();
        if (b == null) {
            b = new JobCardBilling();
            b.setJobCard(jc);
            jc.setBilling(b);
        }
        double discount   = b.getDiscount();
        double taxable    = subTotal - discount;
        double cgstAmt    = taxable * b.getCgstRate()  / 100;
        double sgstAmt    = taxable * b.getSgstRate()  / 100;
        double igstAmt    = taxable * b.getIgstRate()  / 100;
        double grandTotal = taxable + cgstAmt + sgstAmt + igstAmt;
        b.setLabourTotal(labourTotal);
        b.setPartsTotal(partsTotal);
        b.setAncillaryTotal(ancTotal);
        b.setSubTotal(subTotal);
        b.setTaxableAmount(taxable);
        b.setCgstAmount(cgstAmt);
        b.setSgstAmount(sgstAmt);
        b.setIgstAmount(igstAmt);
        b.setGrandTotal(grandTotal);
        b.setBalanceDue(grandTotal - b.getAdvanceAmount());
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    // Deducting is the authoritative "is there enough stock" check, so failures propagate
    // and abort the part addition (see addPart).
    private void deductStock(Long dealerId, String partNumber, int quantity) {
        if (partNumber == null || partNumber.isBlank() || quantity <= 0) return;
        callInventory(INVENTORY_DEDUCT_URL, dealerId, partNumber, quantity, true);
    }

    // Restoring on removal is best-effort cleanup — a removal shouldn't fail because the
    // inventory call had trouble.
    private void restoreStock(Long dealerId, String partNumber, int quantity) {
        if (partNumber == null || partNumber.isBlank() || quantity <= 0) return;
        callInventory(INVENTORY_RESTORE_URL, dealerId, partNumber, quantity, false);
    }

    private void callInventory(String url, Long dealerId, String sku, int quantity, boolean propagateFailure) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", String.valueOf(dealerId));
        Map<String, Object> body = Map.of("sku", sku, "quantity", quantity);
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Void.class);
        } catch (HttpClientErrorException e) {
            if (!propagateFailure) {
                log.warn("Inventory update failed for part {}: {}", sku, e.getMessage());
                return;
            }
            throw new ResponseStatusException(e.getStatusCode(), extractInventoryError(e, sku));
        } catch (Exception e) {
            if (!propagateFailure) {
                log.warn("Inventory update failed for part {}: {}", sku, e.getMessage());
                return;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Inventory service unavailable");
        }
    }

    private String extractInventoryError(HttpClientErrorException e, String sku) {
        try {
            Map<?, ?> body = new ObjectMapper().readValue(e.getResponseBodyAsString(), Map.class);
            Object msg = body.get("error");
            if (msg != null) return msg.toString();
        } catch (Exception ignored) {
            // fall through to generic message below
        }
        return "Could not deduct stock for part: " + sku;
    }

    // ── Job card number generation ─────────────────────────────────────────────

    private String generateJobCardNumber() {
        // 1. Use LocalDateTime to get both Date and Time
        LocalDateTime now = LocalDateTime.now();

        // 2. Extract just the date part for your repository count query
        LocalDate today = now.toLocalDate();
        long countToday = jobCardRepository.countByDate(today);

        // 3. Format the 'now' object (LocalDateTime) which supports Time fields
        return String.format("JC-%s-%04d",
                now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                countToday + 1);
    }
}
