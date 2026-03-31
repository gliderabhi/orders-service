package com.sevis.ordersservice.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AncillaryItemRequest {
    private String description;
    private double amount;
}
