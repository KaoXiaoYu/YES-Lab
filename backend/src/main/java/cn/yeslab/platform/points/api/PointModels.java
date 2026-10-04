package cn.yeslab.platform.points.api;

import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.points.model.PointCategory;
import cn.yeslab.platform.points.model.PointGrantType;
import cn.yeslab.platform.points.model.PointSubcategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PointModels {

    private PointModels() {
    }

    public record ManualGrantRequest(
            @NotBlank @Size(max = 160) String title,
            @NotNull PointSubcategory subcategory,
            @NotNull @PastOrPresent LocalDate occurredOn,
            @Min(1) @Max(100000) int itemTotalPoints,
            @NotNull UUID requestKey,
            UUID competitionId,
            UUID projectId,
            @Size(max = 1000) String description,
            @NotEmpty @Size(max = 100) List<@Valid AllocationRequest> allocations
    ) { }

    public record SourceMember(UUID id, String name, String memberCode, int totalPoints) { }
    public record SourceOption(UUID id, String name, String awardName, List<SourceMember> members) { }
    public record SourceOptions(List<SourceOption> competitions, List<SourceOption> projects) { }

    public record GrantRequest(
            @NotBlank @Size(max = 160) String title,
            @NotNull PointSubcategory subcategory,
            @NotNull @PastOrPresent LocalDate occurredOn,
            @Min(1) @Max(100000) int itemTotalPoints,
            @NotBlank @Size(max = 190) String sourceReference,
            @NotBlank @Size(max = 1000) String evidenceUrl,
            @Size(max = 1000) String description,
            @NotEmpty @Size(max = 100) List<@Valid AllocationRequest> allocations
    ) {
    }

    public record AllocationRequest(
            @NotNull UUID memberProfileId,
            @Min(1) @Max(100000) int points,
            @NotBlank @Size(max = 500) String contribution
    ) {
    }

    public record ReversalRequest(
            @NotBlank @Size(max = 1000) String reason,
            @Size(max = 1000) String evidenceUrl
    ) {
    }

    public record RuleView(
            PointCategory category,
            String categoryLabel,
            PointSubcategory subcategory,
            String subcategoryLabel,
            Integer monthlyCap,
            PointSubcategory.AllocationPolicy allocationPolicy
    ) {
    }

    public record AllocationView(
            UUID memberProfileId,
            String memberName,
            String memberCode,
            int requestedPoints,
            int creditedPoints,
            String contribution,
            int currentTotalPoints
    ) {
    }

    public record GrantView(
            UUID id,
            PointGrantType type,
            UUID reversalOfGrantId,
            PointCategory category,
            String categoryLabel,
            PointSubcategory subcategory,
            String subcategoryLabel,
            String title,
            LocalDate occurredOn,
            int itemTotalPoints,
            int awardedPoints,
            String sourceReference,
            String evidenceUrl,
            String description,
            String operatorUsername,
            Instant createdAt,
            List<AllocationView> allocations,
            UUID competitionId,
            UUID projectId,
            String sourceEntityReference,
            String sourceName
    ) {
    }

    public record EntryView(
            UUID id,
            UUID grantId,
            PointGrantType type,
            UUID reversalOfGrantId,
            PointCategory category,
            String categoryLabel,
            PointSubcategory subcategory,
            String subcategoryLabel,
            String title,
            LocalDate occurredOn,
            int requestedPoints,
            int points,
            String contribution,
            String evidenceUrl,
            String description,
            String operatorUsername,
            Instant createdAt
    ) {
    }

    public record MemberSummary(
            int totalPoints,
            Integer totalRank,
            int participantCount,
            Map<PointCategory, Integer> categoryTotals,
            Map<PointSubcategory, Integer> subcategoryTotals,
            List<EntryView> entries,
            List<DailyPointView> dailyPoints
    ) {
    }

    public enum LeaderboardPeriod {
        TOTAL,
        DAY,
        WEEK,
        MONTH,
        YEAR
    }

    public record DailyPointView(
            LocalDate date,
            int points
    ) {
    }

    public record LeaderboardEntry(
            int rank,
            UUID memberProfileId,
            String memberName,
            String memberCode,
            String avatarUrl,
            Role role,
            int points,
            int totalPoints,
            boolean currentMember
    ) {
    }

    public record LeaderboardView(
            LeaderboardPeriod period,
            LocalDate startsOn,
            LocalDate endsOn,
            Instant generatedAt,
            List<LeaderboardEntry> entries
    ) {
    }
}
