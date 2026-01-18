package zm.gov.moh.zmscproelmispipeline.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagePayload {

    @JsonProperty("msh")
    private MessageHeader msh;

    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageHeader {
        @JsonProperty("timestamp")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private String timestamp;

        @JsonProperty("sendingApplication")
        private String sendingApplication;

        @JsonProperty("receivingApplication")
        private String receivingApplication;

        @JsonProperty("messageId")
        private String messageId;

        @JsonProperty("hmisCode")
        private String hmisCode;

        @JsonProperty("mflCode")
        private String mflCode;

        @JsonProperty("messageType")
        private String messageType;
    }
}