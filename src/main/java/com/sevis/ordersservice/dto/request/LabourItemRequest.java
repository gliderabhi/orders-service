package com.sevis.ordersservice.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LabourItemRequest {
    private String description;
    private String type = "LABOUR";
    private Long   technicianId;
    private int    quantity = 1;
    private double rate;

    // Parts attached at labour-creation time (e.g. engine oil under "Oil change") —
    // more can be attached later via the labour's own "Add Part" action.
    private List<PartItemRequest> parts;
}