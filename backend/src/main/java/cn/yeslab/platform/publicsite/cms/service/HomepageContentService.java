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
                sourceProfile.primaryActionLabel(), sourceProfile.secondaryActionLabel()
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
        return new HomepageModels.HomepageContent(
                profile, sections, List.copyOf(content.proofItems()), List.copyOf(content.updates()),
                List.copyOf(content.awards()), List.copyOf(content.sponsors()), List.copyOf(content.externalLinks()),
                content.advisorProfileId(), unique(content.featuredMemberProfileIds()), unique(content.featuredProjectIds())
        );
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<java.util.UUID> unique(List<java.util.UUID> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }

    private void validate(HomepageModels.HomepageContent content) {
        content.profile().researchDirectionItems().forEach(direction -> {
            if (direction.url() != null && !direction.url().isBlank()) validateNavigationUrl(direction.url());
        });
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
