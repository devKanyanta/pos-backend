package com.devkanyanta.pos.zra;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.devkanyanta.pos.shared.config.PosProperties;
import com.devkanyanta.pos.zra.ZraSubmissionEntity.SubmissionStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Background transmission of queued ZRA submissions with retry/back-off. Runs only when
 * {@code pos.zra.enabled=true}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZraSubmissionProcessor {

    private final ZraSubmissionRepository submissionRepository;

    private final ZraSubmissionWorker worker;

    private final PosProperties properties;

    private final Clock clock;

    @Scheduled(
            initialDelayString = "${pos.zra.processor-initial-delay-ms:20000}",
            fixedDelayString = "${pos.zra.processor-interval-ms:30000}"
    )
    public void runScheduled() {

        if (!properties.getZra().isEnabled()) {
            return;
        }

        try {
            processDue();
        } catch (RuntimeException ex) {
            log.error("ZRA processor run failed: {}", ex.getMessage(), ex);
        }
    }

    /** Processes submissions that are due now. @return number processed */
    public int processDue() {

        List<Long> due = submissionRepository.findDueIds(
                List.of(SubmissionStatus.PENDING, SubmissionStatus.RETRYING),
                LocalDateTime.now(clock),
                PageRequest.of(0, Math.max(1, properties.getZra().getBatchSize())));

        int processed = 0;
        for (Long id : due) {
            try {
                worker.process(id);
                processed++;
            } catch (RuntimeException ex) {
                log.error("ZRA submission {} could not be processed: {}", id, ex.getMessage(), ex);
            }
        }
        return processed;
    }
}
