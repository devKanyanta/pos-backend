package com.devkanyanta.pos.support;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.merchant.MerchantProfileEntity;
import com.devkanyanta.pos.merchant.MerchantProfileService;
import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.exception.NotFoundException;
import com.devkanyanta.pos.shared.exception.ServiceUnavailableException;
import com.devkanyanta.pos.shared.util.PhoneNumbers;
import com.devkanyanta.pos.shared.util.Texts;
import com.devkanyanta.pos.support.dto.ShareResponse;
import com.devkanyanta.pos.support.dto.SupportGuideRequest;
import com.devkanyanta.pos.support.dto.SupportGuideResponse;
import com.devkanyanta.pos.support.dto.WhatsAppHelpResponse;
import com.devkanyanta.pos.whatsapp.WhatsAppService;

import lombok.RequiredArgsConstructor;

/** WhatsApp-first onboarding and support. */
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportGuideRepository guideRepository;

    private final MerchantProfileService merchantProfileService;

    private final WhatsAppService whatsAppService;

    private final PosProperties properties;

    /** Link for the in-app Help button: a WhatsApp chat with support, pre-filled with the account id. */
    @Transactional
    public WhatsAppHelpResponse helpLink(String topic, String username) {

        String supportNumber = supportNumber();
        if (supportNumber == null) {
            throw new ServiceUnavailableException("WhatsApp support number is not configured (pos.whatsapp.support-number)");
        }

        MerchantProfileEntity merchant = merchantProfileService.getOrCreate();

        StringBuilder message = new StringBuilder("Hello Support, I need help");
        if (StringUtils.hasText(topic)) {
            message.append(" with: ").append(Texts.truncate(topic.trim(), 200));
        }
        message.append(".\nAccount ID: ").append(merchant.getAccountId());
        if (merchant.getBusinessName() != null) {
            message.append("\nBusiness: ").append(merchant.getBusinessName());
        }
        message.append("\nUser: ").append(username);
        message.append("\nPlan: ").append(merchant.getPlan().name());

        String text = message.toString();

        return WhatsAppHelpResponse.builder()
                .supportNumber(supportNumber)
                .accountId(merchant.getAccountId())
                .username(username)
                .message(text)
                .url(whatsAppService.clickToChatLink(supportNumber, text))
                .build();
    }

    @Transactional(readOnly = true)
    public List<SupportGuideResponse> listGuides(String topic, boolean includeInactive) {

        List<SupportGuideEntity> guides;
        if (includeInactive) {
            guides = guideRepository.findAllByOrderByTopicAscSortOrderAscIdAsc();
        } else if (StringUtils.hasText(topic)) {
            guides = guideRepository.findByTopicIgnoreCaseAndActiveTrueOrderBySortOrderAscIdAsc(topic.trim());
        } else {
            guides = guideRepository.findByActiveTrueOrderByTopicAscSortOrderAscIdAsc();
        }

        String accountId = merchantProfileService.getOrCreate().getAccountId();
        return guides.stream().map(guide -> toResponse(guide, accountId)).toList();
    }

    @Transactional(readOnly = true)
    public SupportGuideResponse getGuide(Long id) {
        return toResponse(find(id), merchantProfileService.getOrCreate().getAccountId());
    }

    @Transactional
    public SupportGuideResponse createGuide(SupportGuideRequest request) {

        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BadRequestException("Guide title is required");
        }
        if (!StringUtils.hasText(request.getTopic())) {
            throw new BadRequestException("Guide topic is required");
        }

        SupportGuideEntity guide = SupportGuideEntity.builder()
                .title(Texts.truncate(request.getTitle().trim(), 255))
                .topic(normalizeTopic(request.getTopic()))
                .description(Texts.truncate(Texts.clean(request.getDescription()), 2000))
                .mediaUrl(validateUrl(request.getMediaUrl()))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .active(request.getActive() == null || request.getActive())
                .build();

        return toResponse(guideRepository.save(guide), merchantProfileService.getOrCreate().getAccountId());
    }

    @Transactional
    public SupportGuideResponse updateGuide(Long id, SupportGuideRequest request) {

        if (request == null) {
            throw new BadRequestException("Guide request is required");
        }

        SupportGuideEntity guide = find(id);

        if (request.getTitle() != null) {
            if (!StringUtils.hasText(request.getTitle())) {
                throw new BadRequestException("Guide title cannot be blank");
            }
            guide.setTitle(Texts.truncate(request.getTitle().trim(), 255));
        }
        if (request.getTopic() != null) {
            guide.setTopic(normalizeTopic(request.getTopic()));
        }
        if (request.getDescription() != null) {
            guide.setDescription(Texts.truncate(Texts.clean(request.getDescription()), 2000));
        }
        if (request.getMediaUrl() != null) {
            guide.setMediaUrl(validateUrl(request.getMediaUrl()));
        }
        if (request.getSortOrder() != null) {
            guide.setSortOrder(request.getSortOrder());
        }
        if (request.getActive() != null) {
            guide.setActive(request.getActive());
        }

        return toResponse(guideRepository.save(guide), merchantProfileService.getOrCreate().getAccountId());
    }

    @Transactional
    public void deleteGuide(Long id) {
        guideRepository.delete(find(id));
    }

    /** Sends a guide (title, description and link) to a merchant's WhatsApp. */
    @Transactional
    public ShareResponse sendGuide(Long id, String phone, boolean send, String username) {

        SupportGuideEntity guide = find(id);
        String text = guideText(guide);

        return ShareResponse.builder()
                .text(text)
                .shareUrl(whatsAppService.clickToChatLink(phone, text))
                .sent(send ? whatsAppService.send(phone, text, "SUPPORT_GUIDE", guide.getId(), username) : null)
                .build();
    }

    private String guideText(SupportGuideEntity guide) {

        StringBuilder text = new StringBuilder("*").append(guide.getTitle()).append("*");
        if (guide.getDescription() != null) {
            text.append("\n").append(guide.getDescription());
        }
        if (guide.getMediaUrl() != null) {
            text.append("\n").append(guide.getMediaUrl());
        }
        return text.toString();
    }

    private SupportGuideResponse toResponse(SupportGuideEntity guide, String accountId) {

        String supportNumber = supportNumber();
        String requestUrl = supportNumber == null ? null : whatsAppService.clickToChatLink(supportNumber,
                "Hello Support, please send me the guide: " + guide.getTitle() + "\nAccount ID: " + accountId);

        return SupportGuideResponse.builder()
                .id(guide.getId())
                .title(guide.getTitle())
                .topic(guide.getTopic())
                .description(guide.getDescription())
                .mediaUrl(guide.getMediaUrl())
                .sortOrder(guide.getSortOrder())
                .active(guide.getActive())
                .requestViaWhatsAppUrl(requestUrl)
                .updatedAt(guide.getUpdatedAt())
                .build();
    }

    private SupportGuideEntity find(Long id) {
        return guideRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Support guide not found"));
    }

    private String supportNumber() {
        String raw = properties.getWhatsapp().getSupportNumber();
        return StringUtils.hasText(raw)
                ? PhoneNumbers.normalize(raw, properties.getWhatsapp().getDefaultCountryCode())
                : null;
    }

    private static String normalizeTopic(String topic) {
        String value = Texts.clean(topic);
        if (value == null) {
            throw new BadRequestException("Guide topic is required");
        }
        return Texts.truncate(value.toUpperCase().replaceAll("[^A-Z0-9]+", "_"), 40);
    }

    private static String validateUrl(String url) {
        String value = Texts.clean(url);
        if (value != null && !value.matches("(?i)https?://\\S+")) {
            throw new BadRequestException("Media URL must start with http:// or https://");
        }
        return Texts.truncate(value, 500);
    }
}
