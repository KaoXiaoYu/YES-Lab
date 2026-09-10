package cn.yeslab.platform.recruitment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class InterviewRetentionScheduler {
    private final InterviewRetentionService retention;
    private final Duration cancelledSessionRetention;

    public InterviewRetentionScheduler(InterviewRetentionService retention,
            @Value("${yeslab.retention.cancelled-interview-session:PT24H}") Duration cancelledSessionRetention) {
        this.retention = retention;
        this.cancelledSessionRetention = cancelledSessionRetention;
    }

    @Scheduled(cron = "${yeslab.retention.interview-cleanup-cron:0 17 * * * *}", zone = "UTC")
    public void purgeExpiredSessions() {
        retention.purgeExpiredBefore(Instant.now().minus(cancelledSessionRetention));
    }
}
