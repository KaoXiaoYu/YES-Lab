package cn.openlims.platform.fund.api;
import cn.openlims.platform.fund.model.FundEntryType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class FundModels {
    private FundModels() {}
    public record EntryRequest(@NotNull FundEntryType type,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull LocalDate occurredOn, @NotBlank @Size(max = 160) String title,
            @Size(max = 1000) String description, @NotNull UUID requestKey) {}
    public record ReverseRequest(@NotBlank @Size(max = 1000) String reason, @NotNull UUID requestKey) {}
    public record Summary(boolean initialized, String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal openingBalance,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal income,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal expense,
            LocalDate openedOn, Instant updatedAt) {}
    public record EntryView(UUID id, FundEntryType type,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            LocalDate occurredOn, String title, String description, String operatorName, Instant recordedAt,
            UUID originalEntryId, String reversalReason, UUID reversalEntryId, String reversedBy,
            Instant reversedAt, String reason) {}
    public record EntryPage(List<EntryView> entries, long totalCount, int page, int pageSize) {}
}
