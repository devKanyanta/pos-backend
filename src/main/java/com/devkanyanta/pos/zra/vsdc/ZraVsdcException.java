package com.devkanyanta.pos.zra.vsdc;

/** Failure talking to the VSDC: transport error or a non-success result code. */
public class ZraVsdcException extends RuntimeException {

    private final String code;

    private final String responseBody;

    public ZraVsdcException(String code, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.responseBody = responseBody;
    }

    /** VSDC resultCd, HTTP_xxx, or NETWORK. */
    public String getCode() {
        return code;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
