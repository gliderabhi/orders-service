package com.sevis.ordersservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateJobCardRequest {

    @Valid @NotNull(message = "Customer details are required")
    private CustomerData customer;

    @Valid @NotNull(message = "Vehicle details are required")
    private VehicleData vehicle;

    @NotBlank(message = "Service type is required")
    private String serviceType;

    @NotNull(message = "KM reading is required")
    @Min(value = 0, message = "KM reading cannot be negative")
    private Integer kmIn;

    private String expectedDelivery;    // "YYYY-MM-DD"
    private String customerComplaint;
    private String advisorName;

    @Valid
    private List<LabourItemData> labourItems = new ArrayList<>();

    @Valid
    private List<PartItemData> parts = new ArrayList<>();

    @Valid
    private List<AncillaryItemData> ancillaryItems = new ArrayList<>();

    private ChecksData checks;
    private BillingData billing;

    // ── Nested types ──────────────────────────────────────────────────────────

    @Data
    public static class CustomerData {
        @NotBlank(message = "Customer phone is required")
        private String phone;

        @NotBlank(message = "Customer name is required")
        private String name;

        private String email;
        private String address;
    }

    @Data
    public static class VehicleData {
        @NotBlank(message = "Vehicle registration number is required")
        private String regNumber;

        @NotBlank(message = "Vehicle make is required")
        private String make;

        @NotBlank(message = "Vehicle model is required")
        private String model;

        private String variant;
        private Integer year;
        private String chassisNo;
        private String engineNo;
        private String color;
        private String fuelType;
    }

    @Data
    public static class LabourItemData {
        @NotBlank(message = "Labour description is required")
        private String description;

        private String type = "LABOUR";

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity = 1;

        @Min(value = 0, message = "Rate cannot be negative")
        private double rate;
    }

    @Data
    public static class PartItemData {
        @NotBlank(message = "Part number is required")
        private String partNumber;

        @NotBlank(message = "Part description is required")
        private String description;

        private String partType = "OEM";

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity = 1;

        @Min(value = 0, message = "Unit price cannot be negative")
        private double unitPrice;
    }

    @Data
    public static class AncillaryItemData {
        @NotBlank(message = "Description is required")
        private String description;

        @Min(value = 0, message = "Amount cannot be negative")
        private double amount;
    }

    @Data
    public static class ChecksData {
        @Min(0) @Max(100)
        private int fuelLevel = 0;

        private String tireFLPsi;
        private String tireRLPsi;
        private String tireFRPsi;
        private String tireRRPsi;
        private String tireSparePsi;

        private boolean hasToolKit;
        private boolean hasStepney;
        private boolean hasBrochure;
        private boolean hasInsurance;
        private boolean hasPUC;
        private boolean hasRC;
        private String notes;
    }

    @Data
    public static class BillingData {
        @Min(0) private double discount = 0;
        @Min(0) @Max(100) private double cgstRate = 0;
        @Min(0) @Max(100) private double sgstRate = 0;
        @Min(0) @Max(100) private double igstRate = 0;
        @Min(0) private double advanceAmount = 0;
        private String paymentType = "CASH";
    }
}
