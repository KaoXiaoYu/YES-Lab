package cn.yeslab.platform.member.service;

import cn.yeslab.platform.achievement.service.AchievementService;
import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.member.api.MemberManagementModels;
import cn.yeslab.platform.member.api.MemberProfileModels;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;

@Service
public class MemberProfileService {

    private static final PolicyFactory PROFILE_HTML_POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "h2", "h3", "blockquote", "ul", "ol", "li", "strong", "em", "s", "code", "pre", "br", "hr", "a")
            .allowWithoutAttributes("p", "h2", "h3", "blockquote", "ul", "ol", "li", "strong", "em", "s", "code", "pre", "br", "hr")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href").onElements("a")
            .requireRelNofollowOnLinks()
            .toFactory();

    private final MemberProfileRepository profiles;
    private final AuthService authService;
    private final AchievementService achievements;

    public MemberProfileService(MemberProfileRepository profiles, AuthService authService, AchievementService achievements) {
        this.profiles = profiles;
        this.authService = authService;
        this.achievements = achievements;
    }

    @PreAuthorize("hasAuthority('PROFILE_SELF_EDIT')")
    @Transactional(readOnly = true)
    public MemberProfileModels.ProfileView getOwnProfile(Authentication authentication) {
        AccountEntity account = authService.requireAccount(authentication);
        return toView(requireProfile(account));
    }

    @PreAuthorize("hasAuthority('PROFILE_SELF_EDIT')")
    @Transactional
    public MemberProfileModels.ProfileView updateOwnProfile(
            Authentication authentication,
            MemberProfileModels.UpdateProfileRequest request
    ) {
        AccountEntity account = authService.requireAccount(authentication);
        MemberProfileEntity profile = requireProfile(account);
        String avatarUrl = normalizeAvatarUrl(request.avatarUrl());
        String safeHtml = PROFILE_HTML_POLICY.sanitize(request.profileHtml());
        profile.updateEditableFields(
                avatarUrl,
                normalize(request.internalContact()),
                normalize(request.headline()),
                safeHtml
        );
        return toView(profiles.save(profile));
    }

    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @Transactional(readOnly = true)
    public List<MemberProfileModels.ProfileView> listManagedMembers() {
        return profiles.findAll().stream()
                .sorted((left, right) -> {
                    int roleOrder = Integer.compare(left.getAccount().getRole().ordinal(), right.getAccount().getRole().ordinal());
                    return roleOrder != 0 ? roleOrder : left.getName().compareToIgnoreCase(right.getName());
                })
                .map(this::toView)
                .toList();
    }

    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @Transactional(readOnly = true)
    public MemberProfileModels.ProfileView getManagedMember(UUID profileId) {
        return toView(requireProfile(profileId));
    }

    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @Transactional
    public MemberProfileModels.ProfileView updateManagedMember(
            UUID profileId,
            MemberManagementModels.UpdateMemberRequest request
    ) {
        MemberProfileEntity profile = requireProfile(profileId);
        if (request.role() == Role.VISITOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "具有成员档案的账号不能设置为游客");
        }

        String memberCode = request.memberCode().trim();
        profiles.findByMemberCodeIgnoreCase(memberCode)
                .filter(existing -> !existing.getId().equals(profileId))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "该成员编号已被使用");
                });

        boolean teacher = request.role() == Role.TEACHER;
        profile.getAccount().setRole(request.role());
        profile.updateManagedFields(
                request.name().trim(),
                memberCode,
                teacher ? null : normalize(request.major()),
                teacher ? null : normalize(request.className()),
                teacher ? null : normalize(request.grade()),
                normalize(request.internalContact()),
                request.status(),
                normalizeTags(request.skillTags())
        );
        return toView(profiles.save(profile));
    }

    @Transactional(readOnly = true)
    public List<MemberManagementModels.PublicProfileView> listPublicProfiles() {
        return profiles.findAll().stream()
                .filter(profile -> profile.getStatus() == MemberStatus.OFFICIAL)
                .sorted((left, right) -> {
                    int roleOrder = Integer.compare(left.getAccount().getRole().ordinal(), right.getAccount().getRole().ordinal());
                    return roleOrder != 0 ? roleOrder : left.getName().compareToIgnoreCase(right.getName());
                })
                .map(this::toPublicView)
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberManagementModels.PublicProfileView getPublicProfile(UUID profileId) {
        MemberProfileEntity profile = requireProfile(profileId);
        if (profile.getStatus() != MemberStatus.OFFICIAL) {
            throw new ApiException(HttpStatus.NOT_FOUND, "该成员主页暂未公开");
        }
        return toPublicView(profile);
    }

    private MemberProfileEntity requireProfile(AccountEntity account) {
        return profiles.findByAccountId(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "当前账号还没有成员资料"));
    }

    private MemberProfileEntity requireProfile(UUID profileId) {
        return profiles.findById(profileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "成员资料不存在"));
    }

    private String normalizeAvatarUrl(String value) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://") && !normalized.startsWith("/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "头像地址需使用 http、https 或站内路径");
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private List<String> normalizeTags(List<String> tags) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        tags.stream().map(this::normalize).filter(value -> value != null).forEach(normalized::add);
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "至少需要一个能力标签");
        }
        return List.copyOf(normalized);
    }

    private MemberManagementModels.PublicProfileView toPublicView(MemberProfileEntity profile) {
        boolean teacher = profile.getAccount().getRole() == Role.TEACHER;
        return new MemberManagementModels.PublicProfileView(
                profile.getId(),
                profile.getAccount().getRole(),
                profile.getName(),
                teacher ? null : profile.getMajor(),
                teacher ? null : profile.getClassName(),
                teacher ? null : profile.getGrade(),
                profile.getAvatarUrl(),
                profile.getStatus(),
                profile.getSkillTags(),
                profile.getHeadline(),
                profile.getProfileHtml(),
                teacher ? null : profile.getTotalPoints(),
                teacher ? null : profile.getCurrentRank(),
                profile.getProjectRecords(),
                achievementRecords(profile),
                profile.getUpdatedAt()
        );
    }

    private MemberProfileModels.ProfileView toView(MemberProfileEntity profile) {
        return new MemberProfileModels.ProfileView(
                profile.getId(),
                profile.getAccount().getUsername(),
                profile.getAccount().getRole(),
                profile.getName(),
                profile.getMemberCode(),
                profile.getMajor(),
                profile.getClassName(),
                profile.getGrade(),
                profile.getAvatarUrl(),
                profile.getInternalContact(),
                profile.getStatus(),
                profile.getSkillTags(),
                profile.getHeadline(),
                profile.getProfileHtml(),
                profile.getTotalPoints(),
                profile.getCurrentRank(),
                List.of(),
                profile.getProjectRecords(),
                achievementRecords(profile),
                profile.getUpdatedAt()
        );
    }

    private List<String> achievementRecords(MemberProfileEntity profile) {
        LinkedHashSet<String> records = new LinkedHashSet<>(profile.getAchievementRecords());
        records.addAll(achievements.approvedAchievementsFor(profile.getId()));
        return List.copyOf(records);
    }
}
