package cn.yeslab.platform.publicsite.cms.service;

import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.publicsite.cms.api.HomepageModels;
import cn.yeslab.platform.publicsite.cms.model.HomepageContentEntity;
import cn.yeslab.platform.publicsite.cms.repository.HomepageContentRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class HomepageContentService {
    private final HomepageContentRepository repository;
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    public HomepageContentService(HomepageContentRepository repository, ObjectMapper objectMapper, AuthService authService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public HomepageModels.HomepageContent publicContent() {
        return repository.findById(1L).map(this::deserialize).orElseGet(HomepageModels::defaultContent);
    }

    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    @Transactional(readOnly = true)
    public HomepageModels.HomepageAdminView adminContent() {
        return repository.findById(1L)
                .map(entity -> new HomepageModels.HomepageAdminView(deserialize(entity), entity.getUpdatedAt(), entity.getUpdatedBy()))
                .orElseGet(() -> new HomepageModels.HomepageAdminView(HomepageModels.defaultContent(), null, null));
    }

    @PreAuthorize("hasAuthority('CONTENT_MANAGE')")
    @Transactional
    public HomepageModels.HomepageAdminView update(
            Authentication authentication,
            HomepageModels.HomepageContent content
    ) {
        HomepageModels.HomepageContent normalized = normalize(content);
        validate(normalized);
        AccountEntity actor = authService.requireAccount(authentication);
        String serialized = serialize(normalized);
        HomepageContentEntity entity = repository.findById(1L)
                .orElseGet(() -> new HomepageContentEntity(serialized, actor.getUsername()));
        entity.update(serialized, actor.getUsername());
        HomepageContentEntity saved = repository.save(entity);
        return new HomepageModels.HomepageAdminView(deserialize(saved), saved.getUpdatedAt(), saved.getUpdatedBy());
    }

    private HomepageModels.HomepageContent normalize(HomepageModels.HomepageContent content) {
        HomepageModels.ProfileContent sourceProfile = content.profile();
        List<HomepageModels.ResearchDirectionItem> directionItems = sourceProfile.researchDirectionItems();
        if (directionItems == null || directionItems.isEmpty()) {
            directionItems = sourceProfile.researchDirections().stream()
                    .map(name -> new HomepageModels.ResearchDirectionItem(name, "#projects"))
                    .toList();
        } else {
            directionItems = List.copyOf(directionItems);
        }
        HomepageModels.ProfileContent profile = new HomepageModels.ProfileContent(
                sourceProfile.name(), sourceProfile.displayName(), sourceProfile.fullName(), sourceProfile.slogan(),
                sourceProfile.description(), directionItems.stream().map(HomepageModels.ResearchDirectionItem::name).toList(),
                directionItems, sourceProfile.heroEyebrow(), sourceProfile.heroTitle(), sourceProfile.heroAccent(),
                sourceProfile.primaryActionLabel(), sourceProfile.secondaryActionLabel(),
                blankToDefault(sourceProfile.primaryActionUrl(), "#projects"),
                defaultBoolean(sourceProfile.primaryActionEnabled(), true),
                blankToDefault(sourceProfile.secondaryActionUrl(), "#partners"),
                defaultBoolean(sourceProfile.secondaryActionEnabled(), true)
        );

        HomepageModels.AboutSection sourceAbout = content.sections().about();
        HomepageModels.AboutSection defaults = HomepageModels.defaultContent().sections().about();
        HomepageModels.AboutSection about = new HomepageModels.AboutSection(
                sourceAbout.eyebrow(), sourceAbout.title(), sourceAbout.paragraphOne(), sourceAbout.paragraphTwo(),
                List.copyOf(sourceAbout.principles()), sourceAbout.awardsDescription(),
                blankToDefault(sourceAbout.featureEyebrow(), defaults.featureEyebrow()),
                blankToDefault(sourceAbout.featureTitle(), defaults.featureTitle()),
                sourceAbout.features() == null ? HomepageModels.defaultAboutFeatures() : List.copyOf(sourceAbout.features())
        );
        HomepageModels.PageSections sections = new HomepageModels.PageSections(
                content.sections().projects(), about, content.sections().members(), content.sections().partners(),
                content.sections().achievements(), content.sections().contact(), content.sections().footerText()
        );
        List<HomepageModels.ProofItem> proofItems = normalizeProofItems(content.proofItems());
        List<java.util.UUID> advisorIds = unique(content.featuredAdvisorProfileIds());
        if (advisorIds.isEmpty() && content.advisorProfileId() != null) {
            advisorIds = List.of(content.advisorProfileId());
        }
        java.util.UUID legacyAdvisorId = advisorIds.isEmpty() ? null : advisorIds.getFirst();
        HomepageModels.HomepageDisplayOptions display = normalizeDisplayOptions(
                content.display(), advisorIds, content.featuredMemberProfileIds(), content.featuredProjectIds()
        );
        return new HomepageModels.HomepageContent(
                profile, sections, proofItems, List.copyOf(content.updates()),
                List.copyOf(content.awards()), List.copyOf(content.sponsors()), List.copyOf(content.externalLinks()),
                legacyAdvisorId, advisorIds, unique(content.featuredMemberProfileIds()),
                unique(content.featuredProjectIds()), display
        );
    }

    private List<HomepageModels.ProofItem> normalizeProofItems(List<HomepageModels.ProofItem> items) {
        List<HomepageModels.ProofMetric> legacyMetrics = List.of(
                HomepageModels.ProofMetric.AWARDS,
                HomepageModels.ProofMetric.DIRECTIONS,
                HomepageModels.ProofMetric.PARTNERS,
                HomepageModels.ProofMetric.PROJECT_STATUS
        );
        List<HomepageModels.ProofItem> normalized = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            HomepageModels.ProofItem item = items.get(index);
            HomepageModels.ProofMetric metric = item.metric() != null
                    ? item.metric()
                    : index < legacyMetrics.size() ? legacyMetrics.get(index) : HomepageModels.ProofMetric.CUSTOM;
            normalized.add(new HomepageModels.ProofItem(
                    item.label(), item.value(), item.detail(), metric,
                    blankToDefault(item.target(), defaultProofTarget(metric))
            ));
        }
        return List.copyOf(normalized);
    }

    private HomepageModels.HomepageDisplayOptions normalizeDisplayOptions(
            HomepageModels.HomepageDisplayOptions source,
            List<java.util.UUID> advisorIds,
            List<java.util.UUID> memberIds,
            List<java.util.UUID> projectIds
    ) {
        HomepageModels.HomepageDisplayOptions defaults = HomepageModels.defaultDisplayOptions();
        if (source == null) {
            return new HomepageModels.HomepageDisplayOptions(
                    defaults.showProjects(), defaults.showAbout(), defaults.showMembers(), defaults.showPartners(),
                    defaults.showAchievements(), defaults.showContact(), defaults.showAdvisor(), defaults.showCoreMembers(),
                    defaults.showLeaderboard(),
                    advisorIds.isEmpty() ? HomepageModels.SelectionMode.AUTO : HomepageModels.SelectionMode.SELECTED,
                    memberIds == null || memberIds.isEmpty() ? HomepageModels.SelectionMode.AUTO : HomepageModels.SelectionMode.SELECTED,
                    projectIds == null || projectIds.isEmpty() ? HomepageModels.SelectionMode.AUTO : HomepageModels.SelectionMode.SELECTED,
                    defaults.advisorLimit(), defaults.projectLimit(), defaults.memberLimit(), defaults.newsLimit(),
                    defaults.competitionLimit(), defaults.sponsorLimit()
            );
        }
        return new HomepageModels.HomepageDisplayOptions(
                defaultBoolean(source.showProjects(), defaults.showProjects()),
                defaultBoolean(source.showAbout(), defaults.showAbout()),
                defaultBoolean(source.showMembers(), defaults.showMembers()),
                defaultBoolean(source.showPartners(), defaults.showPartners()),
                defaultBoolean(source.showAchievements(), defaults.showAchievements()),
                defaultBoolean(source.showContact(), defaults.showContact()),
                defaultBoolean(source.showAdvisor(), defaults.showAdvisor()),
                defaultBoolean(source.showCoreMembers(), defaults.showCoreMembers()),
                defaultBoolean(source.showLeaderboard(), defaults.showLeaderboard()),
                source.advisorSelectionMode() == null
                        ? advisorIds.isEmpty() ? defaults.advisorSelectionMode() : HomepageModels.SelectionMode.SELECTED
                        : source.advisorSelectionMode(),
                source.memberSelectionMode() == null
                        ? memberIds == null || memberIds.isEmpty() ? defaults.memberSelectionMode() : HomepageModels.SelectionMode.SELECTED
                        : source.memberSelectionMode(),
                source.projectSelectionMode() == null
                        ? projectIds == null || projectIds.isEmpty() ? defaults.projectSelectionMode() : HomepageModels.SelectionMode.SELECTED
                        : source.projectSelectionMode(),
                defaultInteger(source.advisorLimit(), defaults.advisorLimit()),
                defaultInteger(source.projectLimit(), defaults.projectLimit()),
                defaultInteger(source.memberLimit(), defaults.memberLimit()),
                defaultInteger(source.newsLimit(), defaults.newsLimit()),
                defaultInteger(source.competitionLimit(), defaults.competitionLimit()),
                defaultInteger(source.sponsorLimit(), defaults.sponsorLimit())
        );
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Boolean defaultBoolean(Boolean value, Boolean fallback) {
        return value == null ? fallback : value;
    }

    private Integer defaultInteger(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private String defaultProofTarget(HomepageModels.ProofMetric metric) {
        return switch (metric) {
            case AWARDS -> "#updates";
            case PARTNERS -> "#partners";
            case DIRECTIONS, PROJECT_STATUS, CUSTOM -> "#projects";
        };
    }

    private List<java.util.UUID> unique(List<java.util.UUID> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }

    private void validate(HomepageModels.HomepageContent content) {
        content.profile().researchDirectionItems().forEach(direction -> {
            if (direction.url() != null && !direction.url().isBlank()) validateNavigationUrl(direction.url());
        });
        validateEnabledNavigationUrl(content.profile().primaryActionEnabled(), content.profile().primaryActionUrl(), "首屏主按钮");
        validateEnabledNavigationUrl(content.profile().secondaryActionEnabled(), content.profile().secondaryActionUrl(), "首屏次按钮");
        content.proofItems().forEach(item -> validateNavigationUrl(item.target()));
        if (content.display().advisorSelectionMode() == HomepageModels.SelectionMode.SELECTED
                && content.featuredAdvisorProfileIds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "手动展示指导老师时至少选择一位教师");
        }
        if (content.display().memberSelectionMode() == HomepageModels.SelectionMode.SELECTED
                && content.featuredMemberProfileIds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "手动展示核心成员时至少选择一位成员");
        }
        if (content.display().projectSelectionMode() == HomepageModels.SelectionMode.SELECTED
                && content.featuredProjectIds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "手动展示项目时至少选择一个项目");
        }
        content.sponsors().forEach(sponsor -> {
            validateUrl(sponsor.websiteUrl(), "赞助商官网", false);
            validateUrl(sponsor.logoUrl(), "赞助商 Logo", true);
        });
        content.externalLinks().forEach(link -> {
            if (link.enabled() && (link.url() == null || link.url().isBlank())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "启用的外部入口必须填写链接");
            }
            if (link.url() != null && !link.url().isBlank()) validateUrl(link.url(), "外部入口", false);
        });
    }

    private void validateEnabledNavigationUrl(Boolean enabled, String value, String label) {
        if (Boolean.FALSE.equals(enabled)) return;
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "启用时必须填写跳转地址");
        }
        validateNavigationUrl(value);
    }

    private void validateNavigationUrl(String value) {
        if ((value.startsWith("#") && value.length() > 1) || (value.startsWith("/") && !value.startsWith("//"))) return;
        validateUrl(value, "研究方向跳转", false);
    }

    private void validateUrl(String value, String label, boolean allowRelative) {
        if (allowRelative && value.startsWith("/")) return;
        try {
            URI uri = URI.create(value);
            boolean isHttp = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            if (!isHttp || uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + "地址需使用 http、https" + (allowRelative ? " 或站内绝对路径" : ""));
        }
    }

    private String serialize(HomepageModels.HomepageContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JacksonException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "主页内容保存失败");
        }
    }

    private HomepageModels.HomepageContent deserialize(HomepageContentEntity entity) {
        try {
            return normalize(objectMapper.readValue(entity.getContentJson(), HomepageModels.HomepageContent.class));
        } catch (JacksonException error) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "主页内容读取失败");
        }
    }
}
