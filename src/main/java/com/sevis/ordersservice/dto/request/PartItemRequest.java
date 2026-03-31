package com.sevis.ordersservice.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartItemRequest {
    private String partNumber;
    private String description;
    private String partType = "OEM";
    private int    quantity = 1;
    private double unitPrice;
}