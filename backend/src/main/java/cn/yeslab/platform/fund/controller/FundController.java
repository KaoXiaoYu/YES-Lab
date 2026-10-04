package cn.yeslab.platform.fund.controller;
import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.fund.api.FundModels;
import cn.yeslab.platform.fund.model.FundEntryType;
import cn.yeslab.platform.fund.service.FundService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class FundController {
    private final FundService service;
    public FundController(FundService service) { this.service = service; }
    @GetMapping("/public/fund/summary") public ApiResponse<FundModels.Summary> publicSummary() { return ApiResponse.ok(service.publicSummary()); }
    @GetMapping("/fund") public ApiResponse<FundModels.Summary> summary(Authentication auth) { return ApiResponse.ok(service.summary(auth)); }
    @GetMapping("/fund/entries") public ApiResponse<FundModels.EntryPage> entries(Authentication auth,
            @RequestParam(required = false) FundEntryType type, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) { return ApiResponse.ok(service.list(auth, type, from, to, page, pageSize)); }
    @PostMapping("/fund/initialize") public ApiResponse<FundModels.EntryView> initialize(Authentication auth, @Valid @RequestBody FundModels.EntryRequest request) { return ApiResponse.ok(service.create(auth, request, true)); }
    @PostMapping("/fund/entries") public ApiResponse<FundModels.EntryView> create(Authentication auth, @Valid @RequestBody FundModels.EntryRequest request) { return ApiResponse.ok(service.create(auth, request, false)); }
    @PostMapping("/fund/entries/{id}/reverse") public ApiResponse<FundModels.EntryView> reverse(Authentication auth,
            @PathVariable UUID id, @Valid @RequestBody FundModels.ReverseRequest request) { return ApiResponse.ok(service.reverse(auth, id, request)); }
}
