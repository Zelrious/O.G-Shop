package com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnApiResponse<T>(
        int code,
        String message,
        T data,
        @JsonProperty("code_message") String codeMessage,
        @JsonProperty("code_message_value") String codeMessageValue
) {
    public boolean isSuccess() {
        return code == 200;
    }
}
