package com.sevis.ordersservice.service;

import com.sevis.ordersservice.dto.response.InvoiceDetailResponse;
import com.sevis.ordersservice.model.*;
import com.sevis.ordersservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository     invoiceRepository;
    private final CustomerRepository    customerRepository;
    private final VehicleRepository     vehicleRepository;
    private final JobCardRepository     jobCardRepository;
    private final RestTemplate          restTemplate;

    private static final String INVENTORY_DEDUCT_URL =
            "http://inventory-service/api/stock/deduct";

    // ── Validate PDF magic bytes (%PDF) ───────────────────────────────────────
    private static boolean isPdf(byte[] bytes) {
        return bytes.length > 4
                && bytes[0] == 0x25 && bytes[1] == 0x50
                && bytes[2] == 0x44 && bytes[3] == 0x46;
    }

    // ── Upload & process invoice PDF ──────────────────────────────────────────
    @Transactional
    public InvoiceDetailResponse uploadPdf(byte[] pdfBytes, Long dealerId) {
        if (!isPdf(pdfBytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid file. Please upload a valid PDF invoice.");
        }

        InvoicePdfParser.ParsedInvoice parsed;
        try {
            parsed = InvoicePdfParser.parse(pdfBytes);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to parse PDF: " + e.getMessage());
        }

        if (parsed.invoiceNumber() == null || parsed.invoiceNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not extract invoice number from PDF.");
        }

        // Idempotent — return existing if already processed
        if (invoiceRepository.existsByInvoiceNumber(parsed.invoiceNumber())) {
            Invoice existing = invoiceRepository.findByInvoiceNumber(parsed.invoiceNumber()).get();
            return new InvoiceDetailResponse(existing);
        }

        // 1. Find or create customer
        Customer customer = null;
        if (parsed.customerPhone() != null && !parsed.customerPhone().isBlank()) {
            customer = customerRepository.findByPhone(parsed.customerPhone()).orElseGet(() -> {
                Customer c = new Customer();
                c.setPhone(parsed.customerPhone());
                c.setName(parsed.customerName());
                c.setAddress(buildAddress(parsed));
                return customerRepository.save(c);
            });
            // Update name if not set
            if (customer.getName() == null && parsed.customerName() != null) {
                customer.setName(parsed.customerName());
            }
        }

        // 2. Find or create vehicle
        Vehicle vehicle = null;
        if (parsed.vehicleRegNo() != null && !parsed.vehicleRegNo().isBlank()) {
            final Customer finalCustomer = customer;
            vehicle = vehicleRepository.findByRegNumber(parsed.vehicleRegNo().toUpperCase()).orElseGet(() -> {
                Vehicle v = new Vehicle();
                v.setRegNumber(parsed.vehicleRegNo().toUpperCase());
                v.setModel(parsed.vehicleModel());
                v.setChassisNo(parsed.chassisNo());
                if (finalCustomer != null) v.setCustomer(finalCustomer);
                return vehicleRepository.save(v);
            });
            if (vehicle.getChassisNo() == null && parsed.chassisNo() != null) {
                vehicle.setChassisNo(parsed.chassisNo());
            }
        }

        // 3. Find or create job card (always)
        final Customer finalCustomerForJc = customer;
        final Vehicle  finalVehicleForJc  = vehicle;
        JobCard jobCard;
        if (parsed.originalJobCardNumber() != null) {
            jobCard = jobCardRepository.findByExternalJobCardNumber(parsed.originalJobCardNumber())
                    .orElseGet(() -> createJobCardFromInvoice(parsed, dealerId, finalCustomerForJc, finalVehicleForJc));
        } else {
            jobCard = createJobCardFromInvoice(parsed, dealerId, finalCustomerForJc, finalVehicleForJc);
        }
        // If job card was pre-existing (not newly created as DELIVERED), update status
        if (!"DELIVERED".equals(jobCard.getStatus()) && !"CLOSED".equals(jobCard.getStatus())) {
            jobCard.setStatus("DELIVERED");
            jobCard.setDateOut(parsed.invoiceDate() != null ? parsed.invoiceDate() : LocalDate.now());
        }

        // 4. Build invoice
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(parsed.invoiceNumber());
        invoice.setInvoiceDate(parsed.invoiceDate());
        invoice.setJobCard(jobCard);
        invoice.setCustomer(customer);
        invoice.setDealerId(dealerId);
        invoice.setDealerGstin(parsed.dealerGstin());
        invoice.setDealerPan(parsed.dealerPan());
        invoice.setDealerName(parsed.dealerName());
        invoice.setServiceType(parsed.serviceType());
        invoice.setPaymentMethod(parsed.paymentMethod());
        invoice.setOriginalJobCardNumber(parsed.originalJobCardNumber());
        invoice.setJobCardDate(parsed.jobCardDate());
        invoice.setVehicleRegNo(parsed.vehicleRegNo());
        invoice.setChassisNo(parsed.chassisNo());
        invoice.setKms(parsed.kms() > 0 ? parsed.kms() : null);
        invoice.setPlaceOfSupply(parsed.placeOfSupply());
        invoice.setPreparedBy(parsed.preparedBy());
        invoice.setPartsNetTaxableAmount(parsed.partsNetTaxableAmount());
        invoice.setTotalTaxAmount(parsed.totalTaxAmount());
        invoice.setGrandTotal(parsed.grandTotal());
        invoice.setAdjustments(parsed.adjustments());

        // 5. Line items
        for (InvoicePdfParser.LineItem li : parsed.lineItems()) {
            InvoiceLineItem item = new InvoiceLineItem();
            item.setInvoice(invoice);
            item.setLineNumber(li.lineNumber());
            item.setHsnCode(li.hsnCode());
            item.setPartNumber(li.partNumber());
            item.setDescription(li.description());
            item.setType(li.type());
            item.setQuantity(li.quantity());
            item.setRate(li.rate());
            item.setBaseAmount(li.baseAmount());
            item.setDiscountAmount(li.discountAmount());
            item.setTaxableAmount(li.taxableAmount());
            item.setCgstRate(li.cgstRate());
            item.setCgstAmount(li.cgstAmount());
            item.setSgstRate(li.sgstRate());
            item.setSgstAmount(li.sgstAmount());
            item.setTotalAmount(li.totalAmount());
            invoice.getLineItems().add(item);
        }

        Invoice saved = invoiceRepository.save(invoice);

        // 6. Deduct stock for parts with qty > 0
        deductStock(dealerId, parsed.lineItems());

        return new InvoiceDetailResponse(saved);
    }

    // ── Fetch invoice by job card ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<InvoiceDetailResponse> getByJobCard(Long jobCardId) {
        return invoiceRepository.findByJobCardId(jobCardId)
                .stream().map(InvoiceDetailResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse getById(Long id) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        inv.getLineItems().size(); // init lazy
        return new InvoiceDetailResponse(inv);
    }

    // ── Generate PDF ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        inv.getLineItems().size();
        if (inv.getCustomer() != null) inv.getCustomer().getName();
        if (inv.getJobCard() != null) {
            inv.getJobCard().getJobCardNumber();
            if (inv.getJobCard().getVehicle() != null) inv.getJobCard().getVehicle().getRegNumber();
        }
        try {
            return InvoicePdfGenerator.generate(inv);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate PDF: " + e.getMessage());
        }
    }

    // ── Generate / update invoice from job card ───────────────────────────────

    @Transactional
    public InvoiceDetailResponse generateOrUpdateInvoice(Long jobCardId, Long dealerId) {
        JobCard jc = jobCardRepository.findById(jobCardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job card not found"));

        // Init all lazy collections we need
        jc.getLabourItems().size();
        jc.getParts().size();
        jc.getAncillaryItems().size();
        if (jc.getCustomer() != null) jc.getCustomer().getName();
        if (jc.getVehicle() != null) jc.getVehicle().getRegNumber();
        jc.getBilling();

        // Find existing invoice (take first if multiple, which shouldn't happen)
        List<Invoice> existing = invoiceRepository.findByJobCardId(jobCardId);
        Invoice invoice = existing.isEmpty() ? new Invoice() : existing.get(0);
        boolean isNew = invoice.getId() == null;

        if (isNew) {
            // Generate invoice number: INV-YYYYMMDD-NNNN
            LocalDate today = LocalDate.now();
            long seq = invoiceRepository.countByInvoiceDate(today) + 1;
            String invNumber = String.format("INV-%s-%04d",
                    today.format(DateTimeFormatter.ofPattern("yyyyMMdd")), seq);
            invoice.setInvoiceNumber(invNumber);
            invoice.setInvoiceDate(today);
        } else {
            // Update date to today on re-generation
            invoice.setInvoiceDate(LocalDate.now());
            // Clear existing line items — orphanRemoval will delete them
            invoice.getLineItems().clear();
        }

        invoice.setJobCard(jc);
        invoice.setCustomer(jc.getCustomer());
        invoice.setDealerId(dealerId);
        invoice.setOriginalJobCardNumber(jc.getJobCardNumber());
        invoice.setJobCardDate(jc.getDateIn());
        invoice.setServiceType(jc.getServiceType());
        if (jc.getVehicle() != null) {
            invoice.setVehicleRegNo(jc.getVehicle().getRegNumber());
            invoice.setChassisNo(jc.getVehicle().getChassisNo());
        }
        invoice.setKms(jc.getKmIn() > 0 ? jc.getKmIn() : null);

        // Build line items
        int lineNo = 1;
        double labourTaxable = 0, partsTaxable = 0;
        double cgstTotal = 0, sgstTotal = 0;

        JobCardBilling b = jc.getBilling();
        double cgstRate = b != null ? b.getCgstRate() : 0;
        double sgstRate = b != null ? b.getSgstRate() : 0;

        for (JobCardLabour l : jc.getLabourItems()) {
            InvoiceLineItem li = new InvoiceLineItem();
            li.setInvoice(invoice);
            li.setLineNumber(lineNo++);
            li.setDescription(l.getDescription());
            li.setType(l.getType());
            li.setQuantity((double) l.getQuantity());
            li.setRate(l.getRate());
            double base = l.getAmount();
            li.setBaseAmount(base);
            li.setDiscountAmount(0.0);
            li.setTaxableAmount(base);
            double cAmt = base * cgstRate / 100;
            double sAmt = base * sgstRate / 100;
            li.setCgstRate(cgstRate);
            li.setCgstAmount(cAmt);
            li.setSgstRate(sgstRate);
            li.setSgstAmount(sAmt);
            li.setTotalAmount(base + cAmt + sAmt);
            labourTaxable += base;
            cgstTotal += cAmt;
            sgstTotal += sAmt;
            invoice.getLineItems().add(li);
        }

        for (JobCardPart p : jc.getParts()) {
            InvoiceLineItem li = new InvoiceLineItem();
            li.setInvoice(invoice);
            li.setLineNumber(lineNo++);
            li.setPartNumber(p.getPartNumber());
            li.setDescription(p.getDescription());
            li.setType("PARTS");
            li.setQuantity((double) p.getQuantity());
            li.setRate(p.getUnitPrice());
            double base = p.getTotalPrice();
            li.setBaseAmount(base);
            li.setDiscountAmount(0.0);
            li.setTaxableAmount(base);
            double cAmt = base * cgstRate / 100;
            double sAmt = base * sgstRate / 100;
            li.setCgstRate(cgstRate);
            li.setCgstAmount(cAmt);
            li.setSgstRate(sgstRate);
            li.setSgstAmount(sAmt);
            li.setTotalAmount(base + cAmt + sAmt);
            partsTaxable += base;
            cgstTotal += cAmt;
            sgstTotal += sAmt;
            invoice.getLineItems().add(li);
        }

        for (JobCardAncillary a : jc.getAncillaryItems()) {
            InvoiceLineItem li = new InvoiceLineItem();
            li.setInvoice(invoice);
            li.setLineNumber(lineNo++);
            li.setDescription(a.getDescription());
            li.setType("ANCILLARY");
            li.setQuantity(1.0);
            li.setRate(a.getAmount());
            li.setBaseAmount(a.getAmount());
            li.setDiscountAmount(0.0);
            li.setTaxableAmount(a.getAmount());
            double cAmt = a.getAmount() * cgstRate / 100;
            double sAmt = a.getAmount() * sgstRate / 100;
            li.setCgstRate(cgstRate);
            li.setCgstAmount(cAmt);
            li.setSgstRate(sgstRate);
            li.setSgstAmount(sAmt);
            li.setTotalAmount(a.getAmount() + cAmt + sAmt);
            cgstTotal += cAmt;
            sgstTotal += sAmt;
            invoice.getLineItems().add(li);
        }

        invoice.setPartsNetTaxableAmount(partsTaxable);
        invoice.setLabourTaxableAmount(labourTaxable);
        invoice.setCgstAmount(cgstTotal);
        invoice.setSgstAmount(sgstTotal);
        invoice.setIgstAmount(0.0);
        invoice.setTotalTaxAmount(cgstTotal + sgstTotal);
        invoice.setGrandTotal(b != null ? b.getGrandTotal()
                : invoice.getLineItems().stream().mapToDouble(InvoiceLineItem::getTotalAmount).sum());
        invoice.setAdjustments(0.0);

        Invoice saved = invoiceRepository.save(invoice);
        saved.getLineItems().size(); // ensure loaded for response
        return new InvoiceDetailResponse(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private JobCard createJobCardFromInvoice(
            InvoicePdfParser.ParsedInvoice parsed, Long dealerId,
            Customer customer, Vehicle vehicle) {

        JobCard jc = new JobCard();
        jc.setJobCardNumber(generateJobCardNumber());
        jc.setExternalJobCardNumber(parsed.originalJobCardNumber());
        jc.setDealerId(dealerId);
        jc.setCustomer(customer);
        jc.setVehicle(vehicle);
        jc.setServiceType(normalizeServiceType(parsed.serviceType()));
        jc.setStatus("DELIVERED");
        jc.setKmIn(parsed.kms());
        jc.setDateIn(parsed.jobCardDate() != null ? parsed.jobCardDate() : LocalDate.now());
        jc.setDateOut(parsed.invoiceDate() != null ? parsed.invoiceDate() : LocalDate.now());
        return jobCardRepository.save(jc);
    }

    private String generateJobCardNumber() {
        LocalDate today = LocalDate.now();
        long count = jobCardRepository.countByDate(today);
        return String.format("JC-%s-%04d",
                today.format(DateTimeFormatter.ofPattern("yyyyMMdd")), count + 1);
    }

    private String normalizeServiceType(String raw) {
        if (raw == null) return "PERIODIC_SERVICE";
        String u = raw.toUpperCase();
        if (u.contains("FREE")) return "PERIODIC_SERVICE";
        if (u.contains("REPAIR")) return "RUNNING_REPAIR";
        if (u.contains("BODY")) return "BODYWORK";
        if (u.contains("ACCIDENTAL")) return "ACCIDENTAL";
        if (u.contains("WARRANTY")) return "WARRANTY";
        if (u.contains("INSPECTION")) return "INSPECTION";
        return "PERIODIC_SERVICE";
    }

    private String buildAddress(InvoicePdfParser.ParsedInvoice p) {
        StringBuilder sb = new StringBuilder();
        if (p.customerAddress() != null) sb.append(p.customerAddress());
        if (p.customerCity() != null) { if (sb.length() > 0) sb.append(", "); sb.append(p.customerCity()); }
        if (p.customerState() != null) { if (sb.length() > 0) sb.append(", "); sb.append(p.customerState()); }
        if (p.customerPinCode() != null) { if (sb.length() > 0) sb.append(" - "); sb.append(p.customerPinCode()); }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void deductStock(Long dealerId, List<InvoicePdfParser.LineItem> lineItems) {
        List<Map<String, Object>> deductions = new ArrayList<>();
        for (InvoicePdfParser.LineItem li : lineItems) {
            if (li.partNumber() == null || li.partNumber().isBlank()) continue;
            if (li.quantity() <= 0) continue;
            deductions.add(Map.of("partNumber", li.partNumber(), "quantity", (int) li.quantity()));
        }
        if (deductions.isEmpty()) return;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", String.valueOf(dealerId));
        HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(deductions, headers);

        try {
            restTemplate.postForEntity(INVENTORY_DEDUCT_URL, request, Void.class);
        } catch (HttpClientErrorException e) {
            // Stock deduction is best-effort — log and continue
            log.warn("Stock deduction partial/failed for invoice: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Could not reach inventory-service for stock deduction: {}", e.getMessage());
        }
    }
}
