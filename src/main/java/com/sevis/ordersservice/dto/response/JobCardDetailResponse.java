package com.sevis.ordersservice.dto.response;

import com.sevis.ordersservice.model.*;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class JobCardDetailResponse {

    private final Long id;
    private final String jobCardNumber;
    private final String serviceType;
    private final String status;
    private final int kmIn;
    private final String expectedDelivery;
    private final String customerComplaint;
    private final String technicianRemarks;
    private final String advisorName;
    private final String dateIn;
    private final String dateOut;

    private final CustomerInfo customer;
    private final VehicleInfo vehicle;
    private final List<LabourItemInfo> labourItems;
    private final List<PartItemInfo> parts;
    private final List<AncillaryItemInfo> ancillaryItems;
    private final ChecksInfo checks;
    private final BillingInfo billing;

    public JobCardDetailResponse(JobCard jc) {
        this.id                 = jc.getId();
        this.jobCardNumber      = jc.getJobCardNumber();
        this.serviceType        = jc.getServiceType();
        this.status             = jc.getStatus();
        this.kmIn               = jc.getKmIn();
        this.expectedDelivery   = jc.getExpectedDelivery() != null ? jc.getExpectedDelivery().toString() : null;
        this.customerComplaint  = jc.getCustomerComplaint();
        this.technicianRemarks  = jc.getTechnicianRemarks();
        this.advisorName        = jc.getAdvisorName();
        this.dateIn             = jc.getDateIn() != null ? jc.getDateIn().toString() : null;
        this.dateOut            = jc.getDateOut() != null ? jc.getDateOut().toString() : null;

        this.customer = new CustomerInfo(jc.getCustomer());
        this.vehicle  = new VehicleInfo(jc.getVehicle());

        this.labourItems    = jc.getLabourItems().stream().map(LabourItemInfo::new).collect(Collectors.toList());
        this.parts          = jc.getParts().stream().map(PartItemInfo::new).collect(Collectors.toList());
        this.ancillaryItems = jc.getAncillaryItems().stream().map(AncillaryItemInfo::new).collect(Collectors.toList());

        this.checks  = jc.getChecks()  != null ? new ChecksInfo(jc.getChecks())   : null;
        this.billing = jc.getBilling() != null ? new BillingInfo(jc.getBilling())  : null;
    }

    // ── Nested info classes ───────────────────────────────────────────────────

    @Getter
    public static class CustomerInfo {
        private final Long id;
        private final String name;
        private final String phone;
        private final String email;
        private final String address;

        public CustomerInfo(Customer c) {
            this.id      = c.getId();
            this.name    = c.getName();
            this.phone   = c.getPhone();
            this.email   = c.getEmail();
            this.address = c.getAddress();
        }
    }

    @Getter
    public static class VehicleInfo {
        private final Long id;
        private final String regNumber;
        private final String make;
        private final String model;
        private final String variant;
        private final Integer year;
        private final String chassisNo;
        private final String engineNo;
        private final String color;
        private final String fuelType;

        public VehicleInfo(Vehicle v) {
            this.id        = v.getId();
            this.regNumber = v.getRegNumber();
            this.make      = v.getMake();
            this.model     = v.getModel();
            this.variant   = v.getVariant();
            this.year      = v.getYear();
            this.chassisNo = v.getChassisNo();
            this.engineNo  = v.getEngineNo();
            this.color     = v.getColor();
            this.fuelType  = v.getFuelType();
        }
    }

    @Getter
    public static class LabourItemInfo {
        private final Long id;
        private final String description;
        private final String type;
        private final int quantity;
        private final double rate;
        private final double amount;

        public LabourItemInfo(JobCardLabour l) {
            this.id          = l.getId();
            this.description = l.getDescription();
            this.type        = l.getType();
            this.quantity    = l.getQuantity();
            this.rate        = l.getRate();
            this.amount      = l.getAmount();
        }
    }

    @Getter
    public static class PartItemInfo {
        private final Long id;
        private final String partNumber;
        private final String description;
        private final String partType;
        private final int quantity;
        private final double unitPrice;
        private final double totalPrice;

        public PartItemInfo(JobCardPart p) {
            this.id          = p.getId();
            this.partNumber  = p.getPartNumber();
            this.description = p.getDescription();
            this.partType    = p.getPartType();
            this.quantity    = p.getQuantity();
            this.unitPrice   = p.getUnitPrice();
            this.totalPrice  = p.getTotalPrice();
        }
    }

    @Getter
    public static class AncillaryItemInfo {
        private final Long id;
        private final String description;
        private final double amount;

        public AncillaryItemInfo(JobCardAncillary a) {
            this.id          = a.getId();
            this.description = a.getDescription();
            this.amount      = a.getAmount();
        }
    }

    @Getter
    public static class ChecksInfo {
        private final int fuelLevel;
        private final String tireFLPsi;
        private final String tireRLPsi;
        private final String tireFRPsi;
        private final String tireRRPsi;
        private final String tireSparePsi;
        private final boolean hasToolKit;
        private final boolean hasStepney;
        private final boolean hasBrochure;
        private final boolean hasInsurance;
        private final boolean hasPUC;
        private final boolean hasRC;
        private final String notes;

        public ChecksInfo(JobCardChecks c) {
            this.fuelLevel    = c.getFuelLevel();
            this.tireFLPsi    = c.getTireFLPsi();
            this.tireRLPsi    = c.getTireRLPsi();
            this.tireFRPsi    = c.getTireFRPsi();
            this.tireRRPsi    = c.getTireRRPsi();
            this.tireSparePsi = c.getTireSparePsi();
            this.hasToolKit   = c.isHasToolKit();
            this.hasStepney   = c.isHasStepney();
            this.hasBrochure  = c.isHasBrochure();
            this.hasInsurance = c.isHasInsurance();
            this.hasPUC       = c.isHasPUC();
            this.hasRC        = c.isHasRC();
            this.notes        = c.getNotes();
        }
    }

    @Getter
    public static class BillingInfo {
        private final double labourTotal;
        private final double partsTotal;
        private final double ancillaryTotal;
        private final double subTotal;
        private final double discount;
        private final double taxableAmount;
        private final double cgstRate;
        private final double cgstAmount;
        private final double sgstRate;
        private final double sgstAmount;
        private final double igstRate;
        private final double igstAmount;
        private final double grandTotal;
        private final double advanceAmount;
        private final double balanceDue;
        private final String paymentType;

        public BillingInfo(JobCardBilling b) {
            this.labourTotal     = b.getLabourTotal();
            this.partsTotal      = b.getPartsTotal();
            this.ancillaryTotal  = b.getAncillaryTotal();
            this.subTotal        = b.getSubTotal();
            this.discount        = b.getDiscount();
            this.taxableAmount   = b.getTaxableAmount();
            this.cgstRate        = b.getCgstRate();
            this.cgstAmount      = b.getCgstAmount();
            this.sgstRate        = b.getSgstRate();
            this.sgstAmount      = b.getSgstAmount();
            this.igstRate        = b.getIgstRate();
            this.igstAmount      = b.getIgstAmount();
            this.grandTotal      = b.getGrandTotal();
            this.advanceAmount   = b.getAdvanceAmount();
            this.balanceDue      = b.getBalanceDue();
            this.paymentType     = b.getPaymentType();
        }
    }
}
