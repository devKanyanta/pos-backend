package com.devkanyanta.pos.zra.vsdc;

import tools.jackson.databind.JsonNode;

/** Parsed VSDC envelope: {"resultCd":"000","resultMsg":"...","resultDt":"...","data":{...}}. */
public record ZraVsdcResponse(String resultCd, String resultMsg, String resultDt, JsonNode data, String rawBody) {

    public static final String SUCCESS = "000";

    /** "001" = no search result, which is a successful empty lookup. */
    public static final String NO_DATA = "001";

    public boolean isSuccess() {
        return SUCCESS.equals(resultCd) || NO_DATA.equals(resultCd);
    }

    public String dataText(String field) {
        if (data == null || data.isNull()) {
            return null;
        }
        JsonNode node = data.get(field);
        return node == null || node.isNull() ? null : node.asString();
    }
}
