package uz.finbank.finbankauthservice.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uz.finbank.finbankauthservice.entity.enums.PasswordResetTokenStatusEnum;
import uz.finbank.finbankauthservice.entity.enums.SessionStatusEnum;
import uz.finbank.finbankauthservice.repository.PasswordResetTokenRepository;
import uz.finbank.finbankauthservice.repository.SessionRepository;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleRecordCleanupJobTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private StaleRecordCleanupJob newJob(int retentionDays) {
        StaleRecordCleanupJob job = new StaleRecordCleanupJob(sessionRepository, passwordResetTokenRepository);
        ReflectionTestUtils.setField(job, "retentionDays", retentionDays);
        return job;
    }

    @Test
    void run_shouldExpireStaleActiveRecords_thenDeleteOldInactiveRecordsWithinRetentionCutoff() {
        when(sessionRepository.expireStaleActiveSessions(any())).thenReturn(2);
        when(passwordResetTokenRepository.expireStaleActiveTokens(any())).thenReturn(1);
        when(sessionRepository.deleteByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(5);
        when(passwordResetTokenRepository.deleteByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(3);

        newJob(30).run();

        verify(sessionRepository).expireStaleActiveSessions(any());
        verify(passwordResetTokenRepository).expireStaleActiveTokens(any());

        ArgumentCaptor<Collection<SessionStatusEnum>> sessionStatusesCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<LocalDateTime> sessionCutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sessionRepository).deleteByStatusInAndUpdatedAtBefore(sessionStatusesCaptor.capture(), sessionCutoffCaptor.capture());
        assertThat(sessionStatusesCaptor.getValue()).containsExactlyInAnyOrder(
                SessionStatusEnum.REVOKED, SessionStatusEnum.EXPIRED);
        assertThat(sessionCutoffCaptor.getValue())
                .isBefore(LocalDateTime.now().minusDays(29))
                .isAfter(LocalDateTime.now().minusDays(31));

        ArgumentCaptor<Collection<PasswordResetTokenStatusEnum>> tokenStatusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(passwordResetTokenRepository).deleteByStatusInAndUpdatedAtBefore(tokenStatusesCaptor.capture(), any());
        assertThat(tokenStatusesCaptor.getValue()).containsExactlyInAnyOrder(
                PasswordResetTokenStatusEnum.USED, PasswordResetTokenStatusEnum.EXPIRED);
    }
}
