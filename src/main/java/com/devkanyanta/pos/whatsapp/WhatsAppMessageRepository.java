package com.devkanyanta.pos.whatsapp;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessageEntity, Long> {

    List<WhatsAppMessageEntity> findTop100ByOrderByIdDesc();
}
