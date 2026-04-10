package zm.gov.moh.zmscproelmispipeline.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispensedDrug {

    @JsonProperty("mslDrugId")
    private String mslDrugId;

    @JsonProperty("quantityDispensed")
    private Integer quantityDispensed;

    @JsonProperty("doseStrength")
    private String doseStrength;

    @JsonProperty("unitQuantityPerDose")
    private Double unitQuantityPerDose;

    @JsonProperty("frequency")
    private String frequency;

    @JsonProperty("unitOfMeasurement")
    private String unitOfMeasurement;

    @JsonProperty("medicationId")
    private String medicationId;
}
