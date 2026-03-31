package com.sevis.ordersservice.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LabourItemRequest {
    private String description;
    private String type = "LABOUR";
    private int    quantity = 1;
    private double rate;
}