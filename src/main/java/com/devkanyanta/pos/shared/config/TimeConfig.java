package com.devkanyanta.pos.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Pins the application to the business time zone so that "today", business-day reports
 * and ZRA timestamps follow local (Zambian) time regardless of the server's zone.
 */
@Configuration
@Slf4j
public class TimeConfig {

    private final ZoneId zoneId;

    public TimeConfig(PosProperties properties) {
        this.zoneId = ZoneId.of(properties.getTimezone());
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        log.info("Business time zone set to {}", zoneId);
    }

    @Bean
    public Clock businessClock() {
        return Clock.system(zoneId);
    }
}
