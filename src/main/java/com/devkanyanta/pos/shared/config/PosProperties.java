package com.devkanyanta.pos.shared.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Application configuration bound from {@code pos.*} properties (see application.properties).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pos")
public class PosProperties {

    /** Business time zone used for "today", business days and ZRA timestamps. */
    private String timezone = "Africa/Lusaka";

    /** Default low-stock threshold when a product has no reorder level of its own. */
    private int lowStockThreshold = 5;

    private final Jwt jwt = new Jwt();

    private final Zra zra = new Zra();

    private final Sync sync = new Sync();

    private final Reconciliation reconciliation = new Reconciliation();

    private final WhatsApp whatsapp = new WhatsApp();

    @Getter
    @Setter
    public static class Jwt {

        /** HMAC-SHA256 signing secret, at least 32 bytes. */
        private String secret;

        private long expirationMinutes = 1440;
    }

    @Getter
    @Setter
    public static class Zra {

        /** Master switch for transmitting invoices to the ZRA VSDC. */
        private boolean enabled = false;

        /** VSDC API base URL, e.g. the ZRA sandbox or a locally installed VSDC. */
        private String baseUrl = "https://api-sandbox.zra.org.zm/vsdc-api/v1";

        private int connectTimeoutSeconds = 10;

        private int readTimeoutSeconds = 30;

        /** Attempts before a submission is marked FAILED and needs a manual retry. */
        private int maxAttempts = 10;

        /** Base delay for exponential retry back-off. */
        private int retryBaseDelaySeconds = 60;

        /** Upper bound for the retry back-off. */
        private int retryMaxDelaySeconds = 3600;

        /** How many due submissions the background processor handles per run. */
        private int batchSize = 20;

        /** ZRA refund reason code used for credit notes when the request gives none. */
        private String defaultRefundReasonCode = "06";

        /** Default origin country (ISO 3166-1 alpha-2) for item registration. */
        private String defaultOriginCountry = "ZM";

        /** Default packaging unit code for item registration. */
        private String defaultPackagingUnitCode = "NT";

        /** Default quantity unit code for item registration. */
        private String defaultQuantityUnitCode = "U";

        /** Registrant id / name sent in regrId/regrNm fields. */
        private String registrantId = "POS";
    }

    @Getter
    @Setter
    public static class Sync {

        /** Maximum number of offline sales accepted in one sync request. */
        private int maxBatchSize = 100;

        /** Offline sales older than this many days are rejected. */
        private int maxOfflineAgeDays = 60;

        /** Allowed clock skew for offline sale timestamps that are in the future. */
        private int maxClockSkewMinutes = 10;
    }

    @Getter
    @Setter
    public static class Reconciliation {

        /** Absolute variance per tender tolerated before a line is flagged. */
        private BigDecimal varianceTolerance = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    public static class WhatsApp {

        /** NONE (click-to-chat links only), TWILIO or META (WhatsApp Cloud API). */
        private String provider = "NONE";

        /** Support line in international format without '+', e.g. 260971234567. */
        private String supportNumber;

        /** Default country calling code applied to local numbers such as 0971234567. */
        private String defaultCountryCode = "260";

        private final Twilio twilio = new Twilio();

        private final Meta meta = new Meta();

        @Getter
        @Setter
        public static class Twilio {

            private String apiBaseUrl = "https://api.twilio.com";

            private String accountSid;

            private String authToken;

            /** WhatsApp-enabled sender number in international format without '+'. */
            private String fromNumber;
        }

        @Getter
        @Setter
        public static class Meta {

            private String apiBaseUrl = "https://graph.facebook.com";

            private String apiVersion = "v21.0";

            private String phoneNumberId;

            private String accessToken;
        }
    }
}
