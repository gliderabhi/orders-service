package com.sevis.ordersservice.dto.response;

import com.sevis.ordersservice.model.JobCard;
import lombok.Getter;

@Getter
public class JobCardSummaryResponse {
    private final Long id;
    private final String jobCardNumber;
    private final String customerName;
    private final String customerPhone;
    private final String vehicleRegNumber;
    private final String vehicleMakeModel;
    private final String serviceType;
    private final String status;
    private final String dateIn;
    private final int kmIn;
    private final Double grandTotal;

    public JobCardSummaryResponse(JobCard jc) {
        this.id               = jc.getId();
        this.jobCardNumber    = jc.getJobCardNumber();
        this.customerName     = jc.getCustomer().getName();
        this.customerPhone    = jc.getCustomer().getPhone();
        this.vehicleRegNumber = jc.getVehicle().getRegNumber();
        this.vehicleMakeModel = jc.getVehicle().getMake() + " " + jc.getVehicle().getModel();
        this.serviceType      = jc.getServiceType();
        this.status           = jc.getStatus();
        this.dateIn           = jc.getDateIn() != null ? jc.getDateIn().toString() : null;
        this.kmIn             = jc.getKmIn();
        this.grandTotal       = jc.getBilling() != null ? jc.getBilling().getGrandTotal() : null;
    }
}
