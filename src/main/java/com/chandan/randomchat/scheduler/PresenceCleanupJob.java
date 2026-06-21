package com.chandan.randomchat.scheduler;

import com.chandan.randomchat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceCleanupJob {

    private final PresenceService presenceService;

    /**
     * Runs every 60 seconds (60,000 ms).
     * fixedDelay = wait 60s AFTER previous execution completes.
     * This prevents pile-up if the job takes longer than expected.
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanStalePresence() {
        log.debug("Running presence cleanup job");
        presenceService.cleanStaleEntries();
    }
}
