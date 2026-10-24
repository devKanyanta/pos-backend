package com.devkanyanta.pos.support;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.support.dto.ShareResponse;
import com.devkanyanta.pos.support.dto.SupportGuideRequest;
import com.devkanyanta.pos.support.dto.SupportGuideResponse;
import com.devkanyanta.pos.support.dto.WhatsAppHelpResponse;
import com.devkanyanta.pos.whatsapp.WhatsAppMessageEntity;
import com.devkanyanta.pos.whatsapp.WhatsAppService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    private final WhatsAppService whatsAppService;

    /** In-app Help button: WhatsApp chat with support, pre-filled with the merchant account id. */
    @GetMapping("/whatsapp")
    public WhatsAppHelpResponse getWhatsAppHelp(
            @RequestParam(value = "topic", required = false) String topic,
            Authentication authentication
    ) {
        return supportService.helpLink(topic, authentication.getName());
    }

    @GetMapping("/guides")
    public List<SupportGuideResponse> listGuides(
            @RequestParam(value = "topic", required = false) String topic
    ) {
        return supportService.listGuides(topic, false);
    }

    /** All guides including inactive ones, for the admin screen. */
    @GetMapping("/guides/all")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPORT_MANAGE + "')")
    public List<SupportGuideResponse> listAllGuides() {
        return supportService.listGuides(null, true);
    }

    @GetMapping("/guides/{id}")
    public SupportGuideResponse getGuide(@PathVariable Long id) {
        return supportService.getGuide(id);
    }

    @PostMapping("/guides")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPORT_MANAGE + "')")
    public SupportGuideResponse createGuide(@RequestBody SupportGuideRequest request) {
        return supportService.createGuide(request);
    }

    @PutMapping("/guides/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPORT_MANAGE + "')")
    public SupportGuideResponse updateGuide(@PathVariable Long id, @RequestBody SupportGuideRequest request) {
        return supportService.updateGuide(id, request);
    }

    @DeleteMapping("/guides/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPORT_MANAGE + "')")
    public void deleteGuide(@PathVariable Long id) {
        supportService.deleteGuide(id);
    }

    /**
     * Shares a guide to a WhatsApp number: always returns a click-to-chat link; with
     * {@code send=true} it is also sent through the WhatsApp Business provider.
     */
    @PostMapping("/guides/{id}/share")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WHATSAPP_SEND + "')")
    public ShareResponse shareGuide(
            @PathVariable Long id,
            @RequestParam(value = "send", defaultValue = "false") boolean send,
            @RequestBody com.devkanyanta.pos.support.dto.SendWhatsAppRequest request,
            Authentication authentication
    ) {
        return supportService.sendGuide(id, request.getPhone(), send, authentication.getName());
    }

    /** Recent messages sent through the provider (delivery log). */
    @GetMapping("/whatsapp/messages")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SUPPORT_MANAGE + "')")
    public List<WhatsAppMessageEntity> recentMessages() {
        return whatsAppService.recentMessages();
    }
}
