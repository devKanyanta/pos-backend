package com.devkanyanta.pos.merchant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the merchant profile at startup so read-only requests (dashboard, reports) never have
 * to create it lazily; PostgreSQL rejects writes inside read-only transactions.
 */
@Configuration
public class MerchantProfileSeeder {

    @Bean
    public CommandLineRunner seedMerchantProfile(MerchantProfileService merchantProfileService) {
        return args -> merchantProfileService.getOrCreate();
    }
}
