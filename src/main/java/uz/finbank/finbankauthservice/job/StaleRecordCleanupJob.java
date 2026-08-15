package uz.finbank.finbankauthservice.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;
import uz.finbank.finbankauthservice.repository.PasswordResetTokenRepository;
import uz.finbank.finbankauthservice.repository.SessionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Nothing else ever marks a past-due ACTIVE session/reset-token as EXPIRED (refresh()/
 * confirmReset() just reject them in place), so without this job they'd sit as ACTIVE forever,
 * and REVOKED/EXPIRED/USED rows would accumulate in the DB indefinitely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleRecordCleanupJob {

    private final SessionRepository sessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.cleanup.retention-days:30}")
    private int retentionDays;

    @Scheduled(cron = "${app.cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(retentionDays);

        int expiredSessions = sessionRepository.expireStaleActiveSessions(now);
        int expiredResetTokens = passwordResetTokenRepository.expireStaleActiveTokens(now);

        int deletedSessions = sessionRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(SessionStatusEnum.REVOKED, SessionStatusEnum.EXPIRED), cutoff);
        int deletedResetTokens = passwordResetTokenRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(PasswordResetTokenStatusEnum.USED, PasswordResetTokenStatusEnum.EXPIRED), cutoff);

        log.info("Cleanup: expired {} session(s), {} reset token(s); deleted {} old session(s), {} old reset token(s) "
                        + "(retention: {} days)",
                expiredSessions, expiredResetTokens, deletedSessions, deletedResetTokens, retentionDays);
    }
}
