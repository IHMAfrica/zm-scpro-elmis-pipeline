package zm.gov.moh.zmscproelmispipeline.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispensationPayload {

    @JsonProperty("msh")
    private MessagePayload.MessageHeader msh;

    @JsonProperty("patientGuid")
    private String patientGuid;

    @JsonProperty("artNumber")
    private String artNumber;

    @JsonProperty("hmisCode")
    private String hmisCode;

    @JsonProperty("nextVisitDate")
    private String nextVisitDate;

    @JsonProperty("transactionTime")
    private String transactionTime;

    @JsonProperty("dispensationDate")
    private String dispensationDate;

    @JsonProperty("clinician")
    private String clinician;

    @JsonProperty("dispensedDrugs")
    private List<DispensedDrug> dispensedDrugs;

    @JsonProperty("clinicianId")
    private String clinicianId;

    @JsonProperty("prescriptionUuid")
    private String prescriptionUuid;
}
