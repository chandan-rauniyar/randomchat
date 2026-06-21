package com.chandan.randomchat.scheduler;

import com.chandan.randomchat.model.Ban;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.model.enums.BanType;
import com.chandan.randomchat.repository.BanRepository;
import com.chandan.randomchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BanExpiryJob {

    private final BanRepository banRepository;
    private final UserRepository userRepository;

    /**
     * Runs every 5 minutes (300,000 ms).
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void liftExpiredBans() {
        List<Ban> expiredBans = banRepository
                .findByBanTypeAndIsLiftedFalseAndExpiresAtBefore(BanType.TEMPORARY, Instant.now());

        if (expiredBans.isEmpty()) return;

        log.info("Lifting {} expired temporary bans", expiredBans.size());

        for (Ban ban : expiredBans) {
            // Lift the ban record
            ban.lift("SYSTEM", "Temporary ban expired automatically");
            banRepository.save(ban);

            // Update user's live ban state
            User user = ban.getUser();
            if (user != null && user.getIsBanned()) {
                user.setIsBanned(false);
                user.setBanReason(null);
                user.setBanExpiresAt(null);
                userRepository.save(user);
                log.info("Temporary ban lifted for user {}", user.getId());
            }
        }
    }
}