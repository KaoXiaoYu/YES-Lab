package cn.yeslab.platform.recruitment.form.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.recruitment.form.api.RecruitmentFormModels.*;
import cn.yeslab.platform.recruitment.form.model.RecruitmentFormConfigEntity;
import cn.yeslab.platform.recruitment.form.model.RecruitmentFormVersionEntity;
import cn.yeslab.platform.recruitment.form.repository.RecruitmentFormConfigRepository;
import cn.yeslab.platform.recruitment.form.repository.RecruitmentFormVersionRepository;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RecruitmentFormService {
    private final RecruitmentFormConfigRepository configs;
    private final RecruitmentFormVersionRepository versions;
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentFormValidator validator;
    private final ObjectMapper mapper;
    private final AuthService auth;
    private final TransactionTemplate initialization;

    public RecruitmentFormService(RecruitmentFormConfigRepository configs, RecruitmentFormVersionRepository versions,
            RecruitmentApplicationRepository applications, RecruitmentFormValidator validator, ObjectMapper mapper,
            AuthService auth, PlatformTransactionManager transactions) {
        this.configs = configs; this.versions = versions; this.applications = applications;
        this.validator = validator; this.mapper = mapper; this.auth = auth;
        initialization = new TransactionTemplate(transactions);
        initialization.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Called by the startup runner, independently of demo-account initialization.
    public void initialize() {
        try {
            initialization.executeWithoutResult(status -> {
                if (configs.existsById(1L)) return;
                Schema schema = RecruitmentFormDefaults.schema();
                validator.validate(schema);
                String json = serialize(schema);
                var version = versions.saveAndFlush(new RecruitmentFormVersionEntity(1, json, null, "SYSTEM"));
                configs.saveAndFlush(new RecruitmentFormConfigEntity(json, version.getId()));
            });
        } catch (DataIntegrityViolationException conflict) {
            // A simultaneous application startup may have created the same singleton.
            // Inspect only after the losing transaction has fully rolled back.
            if (!configs.existsById(1L)) throw conflict;
        }
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional(readOnly = true)
    public AdminView adminView() { return view(requireConfig(false)); }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public AdminView saveDraft(Authentication authentication, DraftRequest request) {
        var config = requireConfig(true);
        var actor = auth.requireAccount(authentication);
        requireRevision(config, request.expectedRevision());
        validator.validate(request.schema());
        config.saveDraft(serialize(request.schema()), actor.getUsername());
        configs.flush();
        return view(config);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional
    public PublishedVersion publish(Authentication authentication, PublishRequest request) {
        // Lock before any consistent read: MySQL REPEATABLE READ must see the
        // version committed by a publisher that previously held this lock.
        var config = requireConfig(true);
        var actor = auth.requireAccount(authentication);
        requireRevision(config, request.expectedRevision());
        var current = requireVersion(config.getPublishedVersionId());
        if (config.getPublishedDraftRevision() == config.getDraftRevision()) return published(current);
        // Saving identical content does not manufacture another publication.
        if (current.getSchemaJson().equals(config.getDraftSchemaJson())) {
            config.publish(current.getId());
            return published(current);
        }
        validator.validate(deserialize(config.getDraftSchemaJson()));
        var version = versions.saveAndFlush(new RecruitmentFormVersionEntity(
                current.getVersionNumber() + 1, config.getDraftSchemaJson(), actor.getId(), actor.getUsername()));
        config.publish(version.getId());
        configs.flush();
        return published(version);
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    @Transactional(readOnly = true)
    public PublishedVersion version(UUID versionId) {
        return published(versions.findById(versionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报名表版本不存在")));
    }

    @PreAuthorize("hasAuthority('RECRUITMENT_SELF_VIEW')")
    @Transactional(readOnly = true)
    public ApplicantView ownForm(Authentication authentication) {
        var account = auth.requireAccount(authentication);
        var application = applications.findByApplicantId(account.getId()).orElse(null);
        if (application != null && application.getFormVersionId() == null) return new ApplicantView(true, null);
        UUID versionId = application == null ? requireConfig(false).getPublishedVersionId() : application.getFormVersionId();
        var version = requireVersion(versionId);
        return new ApplicantView(false, new ApplicantVersion(version.getId(), version.getVersionNumber(), deserialize(version.getSchemaJson())));
    }

    private RecruitmentFormConfigEntity requireConfig(boolean lock) {
        return (lock ? configs.lockGlobal() : configs.findById(1L))
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "报名表尚未初始化，请稍后重试"));
    }
    private RecruitmentFormVersionEntity requireVersion(UUID id) {
        return versions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "报名表版本记录缺失，请联系管理员"));
    }
    private void requireRevision(RecruitmentFormConfigEntity config, Long expected) {
        if (expected == null || expected != config.getDraftRevision()) {
            throw new ApiException(HttpStatus.CONFLICT, "报名表草稿已被修改，请刷新后重试", Map.of("expectedRevision", "草稿版本不匹配"));
        }
    }
    private AdminView view(RecruitmentFormConfigEntity config) {
        var version = requireVersion(config.getPublishedVersionId());
        return new AdminView(deserialize(config.getDraftSchemaJson()), config.getDraftRevision(), config.getUpdatedBy(),
                config.getUpdatedAt(), published(version), !config.getDraftSchemaJson().equals(version.getSchemaJson()));
    }
    private PublishedVersion published(RecruitmentFormVersionEntity version) {
        return new PublishedVersion(version.getId(), version.getVersionNumber(), deserialize(version.getSchemaJson()),
                version.getPublishedBy(), version.getPublishedAt());
    }
    private String serialize(Schema schema) {
        try {
            String json = mapper.writeValueAsString(schema);
            if (json.getBytes(StandardCharsets.UTF_8).length > RecruitmentFormDefaults.MAX_SCHEMA_BYTES) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "报名表配置过大", Map.of("schema", "配置不能超过 256KB"));
            }
            return json;
        } catch (JacksonException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "报名表配置无法保存");
        }
    }
    private Schema deserialize(String json) {
        try { return mapper.readValue(json, Schema.class); }
        catch (JacksonException error) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "报名表配置无法读取，请联系管理员"); }
    }
}
