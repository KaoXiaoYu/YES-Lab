package cn.yeslab.platform.deadline.api;
import java.time.*;
import java.util.*;
public final class DeadlineModels {
    private DeadlineModels() {}
    public enum SourceType { STANDARD, BOUNTY, ONBOARDING, COMPETITION, PROJECT }
    public record Participant(String name, String role) {}
    public record Entry(SourceType sourceType, UUID sourceId, String milestoneKey, String title,
            String milestone, LocalDate deadlineDate, Instant deadlineAt, String status, String href,
            List<Participant> participants, long participantCount, String participantVisibility) {}
    public record DeadlinePage(List<Entry> entries, long totalCount, int page, int pageSize, LocalDate serverDate, Instant generatedAt) {}
}
