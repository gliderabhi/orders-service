package com.sevis.ordersservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BillingUpdateRequest {
    @Min(0) private double discount = 0;
    @Min(0) @Max(100) private double cgstRate = 0;
    @Min(0) @Max(100) private double sgstRate = 0;
    @Min(0) @Max(100) private double igstRate = 0;
}
