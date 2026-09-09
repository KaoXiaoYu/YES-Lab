package cn.yeslab.platform.recruitment.api;

import cn.yeslab.platform.recruitment.model.InterviewBookingStatus;
import cn.yeslab.platform.recruitment.model.InterviewSessionStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InterviewScheduleModels {
    private InterviewScheduleModels() { }

    public record SessionRequest(
            @NotNull @Future Instant startAt,
            @NotNull @Future Instant endAt,
            @NotBlank @Size(max = 240) String location,
            @Min(1) @Max(100) int capacity,
            @NotEmpty @Size(max = 20) List<@NotBlank String> interviewerUsernames
    ) { }

    public record InterviewerView(UUID accountId, String username, String name, String role) { }

    public record QueueEntryView(
            UUID bookingId,
            UUID applicationId,
            String applicantName,
            int queueNumber,
            InterviewBookingStatus status,
            Instant bookedAt
    ) { }

    public record AdminSessionView(
            UUID id,
            Instant startAt,
            Instant endAt,
            String location,
            int capacity,
            int bookedCount,
            InterviewSessionStatus status,
            String publisherUsername,
            List<InterviewerView> interviewers,
            List<QueueEntryView> queue,
            boolean currentUserInterviewer
    ) { }

    public record ApplicantSessionView(
            UUID id,
            Instant startAt,
            Instant endAt,
            int remainingPlaces,
            InterviewSessionStatus status
    ) { }

    public record ApplicantBookingView(
            UUID bookingId,
            UUID sessionId,
            Instant startAt,
            Instant endAt,
            String location,
            int queueNumber,
            Integer currentlyCalledNumber,
            InterviewBookingStatus status,
            boolean canCancel
    ) { }

    public record ApplicantScheduleView(
            boolean eligible,
            String message,
            ApplicantBookingView booking,
            List<ApplicantSessionView> availableSessions
    ) { }

    public record InterviewResultRequest(
            @Min(0) @Max(100) Integer score,
            @Size(max = 5000) String evaluation,
            @NotNull List<@NotBlank @Size(max = 80) String> suggestedTags,
            @NotNull Boolean passed
    ) { }
}
