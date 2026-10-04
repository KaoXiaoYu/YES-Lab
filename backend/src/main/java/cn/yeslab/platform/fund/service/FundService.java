package cn.yeslab.platform.fund.service;

import cn.yeslab.platform.fund.api.FundModels;
import cn.yeslab.platform.fund.model.*;
import cn.yeslab.platform.fund.repository.*;
import cn.yeslab.platform.identity.model.*;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.common.error.ApiException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FundService {
    private final FundAccountRepository accounts;
    private final FundEntryRepository entries;
    private final AuthService auth;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    public FundService(FundAccountRepository accounts, FundEntryRepository entries, AuthService auth,
            ObjectMapper mapper, PlatformTransactionManager manager) {
        this.accounts = accounts; this.entries = entries; this.auth = auth; this.mapper = mapper;
        transactions = new TransactionTemplate(manager);
    }
    private AccountEntity member(Authentication authentication, boolean manage) {
        var account = auth.requireAccount(authentication);
        if (account.getRole() == Role.VISITOR || (manage && !account.getRole().permissions().contains(Permission.FUND_MANAGE)))
            throw new ApiException(HttpStatus.FORBIDDEN, manage ? "无基金记账权限" : "基金明细仅成员可查看");
        return account;
    }
    @Transactional(readOnly = true)
    public FundModels.Summary publicSummary() { return summary(accounts.findById("LAB").orElse(null)); }
    @Transactional(readOnly = true)
    public FundModels.Summary summary(Authentication authentication) { member(authentication, false); return publicSummary(); }
    private FundModels.Summary summary(FundAccountEntity a) {
        boolean ready = a != null && a.getInitializedAt() != null;
        return new FundModels.Summary(ready, "CNY", ready ? a.getOpeningBalance() : null,
                ready ? a.getBalance() : null, ready ? a.getIncome() : null, ready ? a.getExpense() : null,
                ready ? a.getOpenedOn() : null, ready ? a.getUpdatedAt() : null);
    }
    @Transactional(readOnly = true)
    public FundModels.EntryPage list(Authentication authentication, FundEntryType type, LocalDate from, LocalDate to, int page, int size) {
        member(authentication, false);
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "分页或日期范围无效");
        var result = entries.filter(type, from, to, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredOn", "recordedAt", "id")));
        return new FundModels.EntryPage(result.getContent().stream().map(this::view).toList(), result.getTotalElements(), page, size);
    }
    public FundModels.EntryView create(Authentication authentication, FundModels.EntryRequest request, boolean initialize) {
        var operator = member(authentication, true);
        if ((initialize && request.type() != FundEntryType.OPENING) || (!initialize && request.type() != FundEntryType.INCOME && request.type() != FundEntryType.EXPENSE))
            throw new ApiException(HttpStatus.BAD_REQUEST, "收支类型无效");
        if (!initialize && request.amount().signum() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "收支金额必须大于零");
        String fingerprint = fingerprint(List.of(initialize ? "INITIALIZE" : "CREATE", request.type(), request.amount().setScale(2), request.occurredOn(), request.title().trim(), request.description() == null ? "" : request.description().trim()));
        return execute(request.requestKey(), operator, fingerprint, () -> {
            // Lock before the first consistent read: InnoDB REPEATABLE READ must see the preceding holder's commit.
            var account = accounts.lockAccount().orElseGet(() -> accounts.saveAndFlush(new FundAccountEntity()));
            var replay = entries.findByRequestKey(request.requestKey().toString());
            if (replay.isPresent()) return replay(replay.get(), operator, fingerprint);
            if (initialize) {
                if (account.getInitializedAt() != null) throw new ApiException(HttpStatus.CONFLICT, "期初余额已经登记");
                account.initialize(request.amount(), request.occurredOn());
            } else {
                if (account.getInitializedAt() == null) throw new ApiException(HttpStatus.CONFLICT, "请先登记期初余额");
                if (request.type() == FundEntryType.EXPENSE && account.getBalance().compareTo(request.amount()) < 0)
                    throw new ApiException(HttpStatus.CONFLICT, "基金余额不足");
                account.apply(request.type(), request.amount(), false);
                validateTotals(account);
            }
            return view(entries.saveAndFlush(new FundEntryEntity(request.type(), request.amount().setScale(2), request.occurredOn(),
                    request.title().trim(), request.description(), operator, auth.current(authentication).displayName(), request.requestKey().toString(), fingerprint, null, null)));
        });
    }
    public FundModels.EntryView reverse(Authentication authentication, UUID id, FundModels.ReverseRequest request) {
        var operator = member(authentication, true);
        String fingerprint = fingerprint(List.of("REVERSE", id, request));
        return execute(request.requestKey(), operator, fingerprint, () -> {
            var account = accounts.lockAccount().orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "请先登记期初余额"));
            var existing = entries.findByRequestKey(request.requestKey().toString());
            if (existing.isPresent()) return replay(existing.get(), operator, fingerprint);
            var original = entries.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "流水不存在"));
            if (original.getType() != FundEntryType.INCOME && original.getType() != FundEntryType.EXPENSE)
                throw new ApiException(HttpStatus.BAD_REQUEST, "只能撤销收入或支出");
            if (entries.findByOriginalId(id).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "该流水已经撤销");
            if (original.getType() == FundEntryType.INCOME && account.getBalance().compareTo(original.getAmount()) < 0)
                throw new ApiException(HttpStatus.CONFLICT, "撤销收入会造成负余额，请先核对后续支出");
            account.apply(original.getType(), original.getAmount(), true);
            validateTotals(account);
            return view(entries.saveAndFlush(new FundEntryEntity(FundEntryType.REVERSAL, original.getAmount(),
                    LocalDate.now(ZoneId.of("Asia/Shanghai")), "撤销：" + original.getTitle().substring(0, Math.min(156, original.getTitle().length())), null, operator, auth.current(authentication).displayName(),
                    request.requestKey().toString(), fingerprint, original, request.reason().trim())));
        });
    }
    private void validateTotals(FundAccountEntity a) {
        for (var amount : List.of(a.getBalance(), a.getIncome(), a.getExpense()))
            if (amount.precision() - amount.scale() > 17) throw new ApiException(HttpStatus.CONFLICT, "累计金额超出账户范围");
    }
    private FundModels.EntryView execute(UUID key, AccountEntity operator, String fingerprint, Supplier<FundModels.EntryView> work) {
        try { return transactions.execute(status -> work.get()); }
        catch (DataIntegrityViolationException collision) {
            // Includes first-account creation races. Retry after the failed transaction has ended.
            return transactions.execute(status -> work.get());
        }
    }
    private FundModels.EntryView replay(FundEntryEntity entry, AccountEntity operator, String fingerprint) {
        if (!entry.getOperator().getId().equals(operator.getId()) || !entry.getRequestFingerprint().equals(fingerprint))
            throw new ApiException(HttpStatus.CONFLICT, "请求编号已用于其他记账内容");
        return view(entry);
    }
    private String fingerprint(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("无法计算记账请求摘要", e); }
    }
    private FundModels.EntryView view(FundEntryEntity e) {
        var reversal = entries.findByOriginalId(e.getId()).orElse(null);
        return new FundModels.EntryView(e.getId(), e.getType(), e.getAmount(), e.getOccurredOn(), e.getTitle(), e.getDescription(),
                e.getOperatorName(), e.getRecordedAt(), e.getOriginal() == null ? null : e.getOriginal().getId(), e.getReversalReason(),
                reversal == null ? null : reversal.getId(), reversal == null ? null : reversal.getOperatorName(),
                reversal == null ? null : reversal.getRecordedAt(), reversal == null ? null : reversal.getReversalReason());
    }
}
