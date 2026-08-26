package cn.yeslab.platform.member.service;

import cn.yeslab.platform.achievement.service.AchievementService;
import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.model.AccountEntity;
import cn.yeslab.platform.identity.model.MemberProfileEntity;
import cn.yeslab.platform.identity.model.MemberStatus;
import cn.yeslab.platform.identity.model.Role;
import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.identity.repository.MemberProfileRepository;
import cn.yeslab.platform.identity.service.AuthService;
import cn.yeslab.platform.member.api.MemberManagementModels;
import cn.yeslab.platform.member.api.MemberProfileModels;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final AccountRepository accounts;
    private final AuthService authService;
    private final AchievementService achievements;
    private final PasswordEncoder passwords;
    private final MemberAvatarStorageService avatars;

    public MemberProfileService(
            MemberProfileRepository profiles,
            AccountRepository accounts,
            AuthService authService,
            AchievementService achievements,
            PasswordEncoder passwords,
            MemberAvatarStorageService avatars
    ) {
        this.profiles = profiles;
        this.accounts = accounts;
        this.authService = authService;
        this.achievements = achievements;
        this.passwords = passwords;
        this.avatars = avatars;
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
        String safeHtml = PROFILE_HTML_POLICY.sanitize(request.profileHtml());
        profile.updateEditableFields(
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

    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @Transactional
    public MemberProfileModels.ProfileView createCoreStudent(
            MemberManagementModels.CreateCoreStudentRequest request
    ) {
        String username = AuthService.normalizeUsername(request.username());
        if (accounts.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "该登录账号已被使用");
        }
        String memberCode = request.memberCode().trim();
        if (profiles.existsByMemberCodeIgnoreCase(memberCode)) {
            throw new ApiException(HttpStatus.CONFLICT, "该成员编号已被使用");
        }

        AccountEntity account = accounts.save(new AccountEntity(
                username,
                passwords.encode(request.temporaryPassword()),
                Role.CORE_STUDENT
        ));
        MemberProfileEntity profile = new MemberProfileEntity(
                account,
                request.name().trim(),
                memberCode,
                normalize(request.major()),
                normalize(request.className()),
                normalize(request.grade()),
                normalize(request.internalContact()),
                request.status(),
                normalizeTags(request.skillTags())
        );
        return toView(profiles.save(profile));
    }

    @PreAuthorize("hasAuthority('PROFILE_SELF_EDIT')")
    @Transactional
    public MemberProfileModels.ProfileView replaceOwnAvatar(Authentication authentication, MultipartFile avatar) {
        return replaceAvatar(requireProfile(authService.requireAccount(authentication)), avatar);
    }

    @PreAuthorize("hasAuthority('PROFILE_SELF_EDIT')")
    @Transactional
    public MemberProfileModels.ProfileView deleteOwnAvatar(Authentication authentication) {
        return deleteAvatar(requireProfile(authService.requireAccount(authentication)));
    }

    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @Transactional
    public MemberProfileModels.ProfileView replaceManagedAvatar(UUID profileId, MultipartFile avatar) {
        return replaceAvatar(requireProfile(profileId), avatar);
    }

    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @Transactional
    public MemberProfileModels.ProfileView deleteManagedAvatar(UUID profileId) {
        return deleteAvatar(requireProfile(profileId));
    }

    @Transactional(readOnly = true)
    public AvatarDownload avatar(UUID profileId) {
        MemberProfileEntity profile = requireProfile(profileId);
        if (profile.getAvatarUrl() == null || !profile.getAvatarUrl().startsWith("/api/v1/public/member-profiles/")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "头像不存在");
        }
        MemberAvatarStorageService.StoredAvatarResource stored = avatars.resource(profileId);
        return new AvatarDownload(stored.resource(), stored.contentType(), stored.originalName());
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

    private MemberProfileModels.ProfileView replaceAvatar(MemberProfileEntity profile, MultipartFile avatar) {
        avatars.store(profile.getId(), avatar);
        profile.updateAvatarUrl("/api/v1/public/member-profiles/" + profile.getId() + "/avatar?v=" + System.currentTimeMillis());
        return toView(profiles.save(profile));
    }

    private MemberProfileModels.ProfileView deleteAvatar(MemberProfileEntity profile) {
        avatars.delete(profile.getId());
        profile.updateAvatarUrl(null);
        return toView(profiles.save(profile));
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

    public record AvatarDownload(Resource resource, String contentType, String originalName) {
    }
}
