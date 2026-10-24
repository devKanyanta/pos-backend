package com.devkanyanta.pos.whatsapp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.ServiceUnavailableException;
import com.devkanyanta.pos.shared.util.PhoneNumbers;
import com.devkanyanta.pos.shared.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * WhatsApp delivery. Click-to-chat links ({@code https://wa.me/...}) always work and need no
 * account; when a provider is configured ({@code pos.whatsapp.provider} = TWILIO or META) the
 * backend can also send messages itself through the WhatsApp Business API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    public static final int MAX_BODY = 4000;

    private final PosProperties properties;

    private final WhatsAppMessageRepository repository;

    private final ObjectMapper objectMapper;

    public String provider() {
        String provider = properties.getWhatsapp().getProvider();
        return StringUtils.hasText(provider) ? provider.trim().toUpperCase() : "NONE";
    }

    public boolean isSendingConfigured() {

        PosProperties.WhatsApp config = properties.getWhatsapp();
        return switch (provider()) {
            case "TWILIO" -> StringUtils.hasText(config.getTwilio().getAccountSid())
                    && StringUtils.hasText(config.getTwilio().getAuthToken())
                    && StringUtils.hasText(config.getTwilio().getFromNumber());
            case "META" -> StringUtils.hasText(config.getMeta().getPhoneNumberId())
                    && StringUtils.hasText(config.getMeta().getAccessToken());
            default -> false;
        };
    }

    public String normalizePhone(String phone) {
        return PhoneNumbers.normalize(phone, properties.getWhatsapp().getDefaultCountryCode());
    }

    /** wa.me link that opens WhatsApp with the text pre-filled (to a number, or to a chooser). */
    public String clickToChatLink(String phone, String text) {

        String target = StringUtils.hasText(phone) ? normalizePhone(phone) : "";
        return "https://wa.me/" + target + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Sends a text message through the configured provider and logs the attempt. */
    public WhatsAppSendResult send(String phone, String text, String context, Long referenceId, String sentBy) {

        if (!isSendingConfigured()) {
            throw new ServiceUnavailableException("WhatsApp sending is not configured (pos.whatsapp.provider). "
                    + "Use the shareUrl click-to-chat link instead.");
        }

        String to = normalizePhone(phone);
        String body = Texts.truncate(text, MAX_BODY);

        WhatsAppMessageEntity message = WhatsAppMessageEntity.builder()
                .toNumber(to)
                .body(body)
                .provider(provider())
                .context(context)
                .referenceId(referenceId)
                .sentBy(sentBy)
                .status("FAILED")
                .build();

        try {
            String providerId = "TWILIO".equals(provider()) ? sendTwilio(to, body) : sendMeta(to, body);
            message.setStatus("SENT");
            message.setProviderMessageId(providerId);
        } catch (RestClientResponseException ex) {
            message.setError(Texts.truncate("HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), 1000));
            log.warn("WhatsApp send to {} failed: {}", to, message.getError());
        } catch (RestClientException ex) {
            message.setError(Texts.truncate(ex.getMessage(), 1000));
            log.warn("WhatsApp send to {} failed: {}", to, ex.getMessage());
        }

        message = repository.save(message);

        return WhatsAppSendResult.builder()
                .messageId(message.getId())
                .to(to)
                .provider(message.getProvider())
                .status(message.getStatus())
                .providerMessageId(message.getProviderMessageId())
                .error(message.getError())
                .shareUrl(clickToChatLink(to, body))
                .sentAt(message.getCreatedAt())
                .build();
    }

    public List<WhatsAppMessageEntity> recentMessages() {
        return repository.findTop100ByOrderByIdDesc();
    }

    private String sendTwilio(String to, String body) {

        PosProperties.WhatsApp.Twilio twilio = properties.getWhatsapp().getTwilio();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", "whatsapp:+" + PhoneNumbers.normalize(twilio.getFromNumber(), properties.getWhatsapp().getDefaultCountryCode()));
        form.add("To", "whatsapp:+" + to);
        form.add("Body", body);

        String response = client(twilio.getApiBaseUrl()).post()
                .uri("/2010-04-01/Accounts/{sid}/Messages.json", twilio.getAccountSid())
                .headers(headers -> headers.setBasicAuth(twilio.getAccountSid(), twilio.getAuthToken()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        JsonNode json = response != null ? objectMapper.readTree(response) : null;
        return json != null && json.has("sid") ? json.get("sid").asString() : null;
    }

    private String sendMeta(String to, String body) {

        PosProperties.WhatsApp.Meta meta = properties.getWhatsapp().getMeta();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", to);
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", true, "body", body));

        String response = client(meta.getApiBaseUrl()).post()
                .uri("/{version}/{phoneNumberId}/messages", meta.getApiVersion(), meta.getPhoneNumberId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + meta.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(payload))
                .retrieve()
                .body(String.class);

        JsonNode json = response != null ? objectMapper.readTree(response) : null;
        if (json != null && json.has("messages") && json.get("messages").size() > 0) {
            return json.get("messages").get(0).get("id").asString();
        }
        return null;
    }

    private RestClient client(String baseUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
