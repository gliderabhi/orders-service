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
import com.sevis.ordersservice.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobCardService {

    private final JobCardRepository    jobCardRepository;
    private final CustomerRepository   customerRepository;
    private final VehicleRepository    vehicleRepository;

    private static final List<String> VALID_SERVICE_TYPES =
            List.of("PERIODIC_SERVICE", "RUNNING_REPAIR", "BODYWORK", "INSPECTION", "ACCIDENTAL", "WARRANTY");
    private static final List<String> VALID_STATUSES =
            List.of("OPEN", "IN_PROGRESS", "READY", "DELIVERED", "CLOSED");

    // ── List ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JobCardSummaryResponse> getAll(Long dealerId) {
        List<JobCard> cards = (dealerId != null)
                ? jobCardRepository.findByDealerIdWithSummary(dealerId)
                : jobCardRepository.findAll();
        return cards.stream().map(JobCardSummaryResponse::new).collect(Collectors.toList());
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public JobCardDetailResponse getById(Long id) {
        JobCard jc = jobCardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job card not found"));
        // Force-init each lazy collection separately — avoids MultipleBagFetchException
        jc.getCustomer().getName();
        jc.getVehicle().getRegNumber();
        jc.getLabourItems().size();
        jc.getParts().size();
        jc.getAncillaryItems().size();
        jc.getChecks();
        jc.getBilling();
        jc.getInvoices().size();
        return new JobCardDetailResponse(jc);
    }

    // ── Create ────────────────────────────────────────────────────────────────

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
        if ("DELIVERED".equals(newStatus) || "CLOSED".equals(newStatus)) {
            jc.setDateOut(LocalDate.now());
        }
        jc.setStatus(newStatus);
        return new JobCardDetailResponse(jobCardRepository.save(jc));
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

    @Transactional
    public JobCardDetailResponse addLabour(Long id, LabourItemRequest req, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        JobCardLabour l = new JobCardLabour();
        l.setJobCard(jc);
        l.setDescription(req.getDescription());
        l.setType(req.getType() != null ? req.getType().toUpperCase() : "LABOUR");
        l.setQuantity(req.getQuantity());
        l.setRate(req.getRate());
        l.setAmount(req.getQuantity() * req.getRate());
        jc.getLabourItems().add(l);
        recalcBilling(jc);
        return new JobCardDetailResponse(jobCardRepository.save(jc));
    }

    @Transactional
    public JobCardDetailResponse deleteLabour(Long id, Long labourId, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        jc.getLabourItems().removeIf(l -> l.getId().equals(labourId));
        recalcBilling(jc);
        return new JobCardDetailResponse(jobCardRepository.save(jc));
    }

    // ── Add / delete parts ────────────────────────────────────────────────────

    @Transactional
    public JobCardDetailResponse addPart(Long id, PartItemRequest req, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        JobCardPart p = new JobCardPart();
        p.setJobCard(jc);
        p.setPartNumber(req.getPartNumber());
        p.setDescription(req.getDescription());
        p.setPartType(req.getPartType() != null ? req.getPartType().toUpperCase() : "OEM");
        p.setQuantity(req.getQuantity());
        p.setUnitPrice(req.getUnitPrice());
        p.setTotalPrice(req.getQuantity() * req.getUnitPrice());
        jc.getParts().add(p);
        recalcBilling(jc);
        return new JobCardDetailResponse(jobCardRepository.save(jc));
    }

    @Transactional
    public JobCardDetailResponse deletePart(Long id, Long partId, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        jc.getParts().removeIf(p -> p.getId().equals(partId));
        recalcBilling(jc);
        return new JobCardDetailResponse(jobCardRepository.save(jc));
    }

    // ── Add / delete ancillary items ──────────────────────────────────────────

    @Transactional
    public JobCardDetailResponse addAncillary(Long id, AncillaryItemRequest req, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        JobCardAncillary a = new JobCardAncillary();
        a.setJobCard(jc);
        a.setDescription(req.getDescription());
        a.setAmount(req.getAmount());
        jc.getAncillaryItems().add(a);
        recalcBilling(jc);
        return new JobCardDetailResponse(jobCardRepository.save(jc));
    }

    @Transactional
    public JobCardDetailResponse deleteAncillary(Long id, Long ancId, Long dealerId) {
        JobCard jc = getForUpdate(id, dealerId);
        jc.getAncillaryItems().removeIf(a -> a.getId().equals(ancId));
        recalcBilling(jc);
        return new JobCardDetailResponse(jobCardRepository.save(jc));
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
