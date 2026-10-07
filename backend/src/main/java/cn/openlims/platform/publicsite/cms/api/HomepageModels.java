package cn.openlims.platform.publicsite.cms.api;

import cn.openlims.platform.config.LabBrand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class HomepageModels {

    private HomepageModels() {
    }

    public record HomepageContent(
            @Valid @NotNull ProfileContent profile,
            @Valid @NotNull PageSections sections,
            @NotNull @Size(max = 6) List<@Valid ProofItem> proofItems,
            @NotNull @Size(max = 20) List<@Valid UpdateItem> updates,
            @NotNull @Size(max = 30) List<@Valid AwardItem> awards,
            @NotNull @Size(max = 20) List<@Valid SponsorItem> sponsors,
            @NotNull @Size(max = 12) List<@Valid ExternalLinkItem> externalLinks,
            UUID advisorProfileId,
            @Size(max = 6) List<@NotNull UUID> featuredAdvisorProfileIds,
            @NotNull @Size(max = 12) List<UUID> featuredMemberProfileIds,
            @NotNull @Size(max = 12) List<UUID> featuredProjectIds,
            @Valid HomepageDisplayOptions display,
            @Size(min = 1, max = 8) List<@Valid HeroModelItem> heroModels
    ) {
        public HomepageContent(
                ProfileContent profile,
                PageSections sections,
                List<ProofItem> proofItems,
                List<UpdateItem> updates,
                List<AwardItem> awards,
                List<SponsorItem> sponsors,
                List<ExternalLinkItem> externalLinks,
                UUID advisorProfileId,
                List<UUID> featuredMemberProfileIds,
                List<UUID> featuredProjectIds
        ) {
            this(profile, sections, proofItems, updates, awards, sponsors, externalLinks,
                    advisorProfileId, null, featuredMemberProfileIds, featuredProjectIds, null, null);
        }

        public HomepageContent(
                ProfileContent profile,
                PageSections sections,
                List<ProofItem> proofItems,
                List<UpdateItem> updates,
                List<AwardItem> awards,
                List<SponsorItem> sponsors,
                List<ExternalLinkItem> externalLinks,
                UUID advisorProfileId,
                List<UUID> featuredMemberProfileIds,
                List<UUID> featuredProjectIds,
                HomepageDisplayOptions display
        ) {
            this(profile, sections, proofItems, updates, awards, sponsors, externalLinks,
                    advisorProfileId, null, featuredMemberProfileIds, featuredProjectIds, display, null);
        }

        public HomepageContent(
                ProfileContent profile,
                PageSections sections,
                List<ProofItem> proofItems,
                List<UpdateItem> updates,
                List<AwardItem> awards,
                List<SponsorItem> sponsors,
                List<ExternalLinkItem> externalLinks,
                UUID advisorProfileId,
                List<UUID> featuredAdvisorProfileIds,
                List<UUID> featuredMemberProfileIds,
                List<UUID> featuredProjectIds,
                HomepageDisplayOptions display
        ) {
            this(profile, sections, proofItems, updates, awards, sponsors, externalLinks,
                    advisorProfileId, featuredAdvisorProfileIds, featuredMemberProfileIds,
                    featuredProjectIds, display, null);
        }
    }

    public record ProfileContent(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(max = 180) String slogan,
            @NotBlank @Size(max = 5000) String description,
            @NotNull @Size(min = 1, max = 12) List<@NotBlank @Size(max = 80) String> researchDirections,
            @Size(min = 1, max = 12) List<@Valid ResearchDirectionItem> researchDirectionItems,
            @NotBlank @Size(max = 120) String heroEyebrow,
            @NotBlank @Size(max = 180) String heroTitle,
            @NotBlank @Size(max = 80) String heroAccent,
            @NotBlank @Size(max = 60) String primaryActionLabel,
            @NotBlank @Size(max = 60) String secondaryActionLabel,
            @Size(max = 800) String primaryActionUrl,
            Boolean primaryActionEnabled,
            @Size(max = 800) String secondaryActionUrl,
            Boolean secondaryActionEnabled
    ) {
        public ProfileContent(
                String name,
                String displayName,
                String fullName,
                String slogan,
                String description,
                List<String> researchDirections,
                List<ResearchDirectionItem> researchDirectionItems,
                String heroEyebrow,
                String heroTitle,
                String heroAccent,
                String primaryActionLabel,
                String secondaryActionLabel
        ) {
            this(name, displayName, fullName, slogan, description, researchDirections, researchDirectionItems,
                    heroEyebrow, heroTitle, heroAccent, primaryActionLabel, secondaryActionLabel,
                    null, null, null, null);
        }
    }

    public record PageSections(
            @Valid @NotNull SectionCopy projects,
            @Valid @NotNull AboutSection about,
            @Valid @NotNull SectionCopy members,
            @Valid @NotNull SectionCopy partners,
            @Valid @NotNull SectionCopy achievements,
            @Valid @NotNull ContactSection contact,
            @NotBlank @Size(max = 180) String footerText
    ) {
    }

    public record SectionCopy(
            @NotBlank @Size(max = 80) String eyebrow,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 500) String description
    ) {
    }

    public record AboutSection(
            @NotBlank @Size(max = 80) String eyebrow,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2000) String paragraphOne,
            @NotBlank @Size(max = 2000) String paragraphTwo,
            @NotNull @Size(max = 8) List<@NotBlank @Size(max = 120) String> principles,
            @Size(max = 300) String awardsDescription,
            @NotBlank @Size(max = 80) String featureEyebrow,
            @NotBlank @Size(max = 180) String featureTitle,
            @NotNull @Size(max = 8) List<@Valid AboutFeatureItem> features
    ) {
    }

    public record ResearchDirectionItem(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 800) String url
    ) {
    }

    public record AboutFeatureItem(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 600) String description
    ) {
    }

    public record ContactSection(
            @NotBlank @Size(max = 80) String eyebrow,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 500) String description
    ) {
    }

    public record ProofItem(
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Size(max = 120) String value,
            @NotBlank @Size(max = 160) String detail,
            ProofMetric metric,
            @Size(max = 800) String target
    ) {
        public ProofItem(String label, String value, String detail) {
            this(label, value, detail, null, null);
        }
    }

    public enum ProofMetric {
        AWARDS,
        DIRECTIONS,
        PARTNERS,
        PROJECT_STATUS,
        CUSTOM
    }

    public enum SelectionMode {
        AUTO,
        SELECTED,
        HIDDEN
    }

    public record HomepageDisplayOptions(
            Boolean showProjects,
            Boolean showAbout,
            Boolean showMembers,
            Boolean showPartners,
            Boolean showAchievements,
            Boolean showContact,
            Boolean showAdvisor,
            Boolean showCoreMembers,
            Boolean showLeaderboard,
            SelectionMode advisorSelectionMode,
            SelectionMode memberSelectionMode,
            SelectionMode projectSelectionMode,
            @Min(1) @Max(6) Integer advisorLimit,
            @Min(1) @Max(12) Integer projectLimit,
            @Min(1) @Max(12) Integer memberLimit,
            @Min(1) @Max(20) Integer newsLimit,
            @Min(1) @Max(30) Integer competitionLimit,
            @Min(1) @Max(20) Integer sponsorLimit
    ) {
        public HomepageDisplayOptions(
                Boolean showProjects,
                Boolean showAbout,
                Boolean showMembers,
                Boolean showPartners,
                Boolean showAchievements,
                Boolean showContact,
                Boolean showAdvisor,
                Boolean showCoreMembers,
                Boolean showLeaderboard,
                SelectionMode advisorSelectionMode,
                SelectionMode memberSelectionMode,
                SelectionMode projectSelectionMode,
                Integer projectLimit,
                Integer memberLimit,
                Integer newsLimit,
                Integer competitionLimit,
                Integer sponsorLimit
        ) {
            this(showProjects, showAbout, showMembers, showPartners, showAchievements, showContact,
                    showAdvisor, showCoreMembers, showLeaderboard, advisorSelectionMode, memberSelectionMode,
                    projectSelectionMode, null, projectLimit, memberLimit, newsLimit, competitionLimit, sponsorLimit);
        }
    }

    public record UpdateItem(
            @NotBlank @Size(max = 40) String publishedAt,
            @NotBlank @Size(max = 80) String type,
            @NotBlank @Size(max = 220) String title,
            @Size(max = 120) String slug
    ) {
    }

    public record AwardItem(
            @NotBlank @Size(max = 180) String competition,
            @NotBlank @Size(max = 180) String category,
            @NotBlank @Size(max = 80) String level,
            @NotBlank @Size(max = 120) String prize
    ) {
    }

    public record SponsorItem(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String type,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @Size(max = 12) List<@NotBlank @Size(max = 100) String> focus,
            @NotBlank @Size(max = 800) String logoUrl,
            @NotBlank @Size(max = 800) String websiteUrl,
            @Size(max = 2000) String cooperationDescription
    ) {
        public SponsorItem(String name, String type, String description, List<String> focus, String logoUrl, String websiteUrl) {
            this(name, type, description, focus, logoUrl, websiteUrl, null);
        }
    }

    public record ExternalLinkItem(
            @NotBlank @Size(max = 40) String platform,
            @NotBlank @Size(max = 80) String label,
            @Size(max = 800) String url,
            boolean enabled
    ) {
    }

    public record HeroModelItem(
            @NotBlank @Size(max = 80) String title,
            @NotBlank @Size(max = 240) String description,
            @NotBlank @Size(max = 800) String modelUrl,
            Boolean enabled
    ) {
    }

    public record HomepageAdminView(HomepageContent content, Instant updatedAt, String updatedBy) {
    }

    public static HomepageContent defaultContent() {
        return new HomepageContent(
                new ProfileContent(
                        LabBrand.NAME, LabBrand.DISPLAY_NAME, LabBrand.FULL_NAME, "连接研究、协作与成长",
                        LabBrand.DESCRIPTION,
                        List.of("基础研究", "交叉探索", "应用实践"),
                        List.of(
                                new ResearchDirectionItem("基础研究", "#projects"),
                                new ResearchDirectionItem("交叉探索", "#projects"),
                                new ResearchDirectionItem("应用实践", "#projects")
                        ),
                        LabBrand.NAME + " · RESEARCH & COLLABORATION", "让每一次探索\n汇聚成", "新的可能", "浏览研究项目", "了解合作伙伴",
                        "#projects", true, "#partners", true
                ),
                new PageSections(
                        new SectionCopy("01 / SELECTED RESEARCH", "研究与工程实践", "从算法、硬件到系统集成，我们以可运行、可验证的真实项目建立研究能力。"),
                        new AboutSection(
                                "02 / ABOUT", "把研究做成\n可以触碰的现场",
                                LabBrand.NAME + " 连接实验室的研究方向、项目实践与成员成长。",
                                "我们以竞赛与真实工程项目为牵引，为学校培养兼具算法、硬件和系统能力的复合型人才。",
                                List.of("面向真实场景", "强调应用实践", "培养工程人才"),
                                "用竞赛检验技术，以成果记录成长。",
                                "HOW WE WORK", "从研究方向走向工程现场",
                                defaultAboutFeatures()
                        ),
                        new SectionCopy("03 / PEOPLE", "共同成长的研究者", "榜单每日刷新"),
                        new SectionCopy("04 / PARTNERS", "赞助与合作伙伴", "连接研究伙伴，共同支持科研实践与人才培养。"),
                        new SectionCopy("05 / ACHIEVEMENTS", "成果与外部报道", "新闻按发布日期自动排序"),
                        new ContactSection("06 / CONNECT", "下一次探索，\n从这里开始。", "关注我们的研究、比赛和开源进展。"),
                        "© " + java.time.Year.now() + " " + LabBrand.NAME + " · RESEARCH & COLLABORATION"
                ),
                List.of(
                        new ProofItem("01 / AWARDS", "竞赛成果", "全国 / 省赛 / 赛区", ProofMetric.AWARDS, "#updates"),
                        new ProofItem("02 / FOCUS", "3 个方向", "持续探索", ProofMetric.DIRECTIONS, "#projects"),
                        new ProofItem("03 / PARTNER", "合作伙伴", "共同探索", ProofMetric.PARTNERS, "#partners"),
                        new ProofItem("04 / STATUS", "持续建设", "开放、实践、成长", ProofMetric.PROJECT_STATUS, "#projects")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new ExternalLinkItem("github", "开源仓库", LabBrand.REPOSITORY_URL, true),
                        new ExternalLinkItem("bilibili", "哔哩哔哩", "", false),
                        new ExternalLinkItem("wechat", "微信公众号", "", false),
                        new ExternalLinkItem("douyin", "抖音", "", false)
                ),
                null,
                List.of(),
                List.of(),
                List.of(),
                defaultDisplayOptions(),
                defaultHeroModels()
        );
    }

    public static HomepageDisplayOptions defaultDisplayOptions() {
        return new HomepageDisplayOptions(
                true, true, true, true, true, true,
                true, true, true,
                SelectionMode.AUTO, SelectionMode.AUTO, SelectionMode.AUTO,
                6, 12, 12, 20, 30, 20
        );
    }

    public static List<AboutFeatureItem> defaultAboutFeatures() {
        return List.of(
                new AboutFeatureItem("真实问题驱动", "从科研实践的真实问题出发，把研究目标拆解为可以验证的算法、硬件与系统方案。"),
                new AboutFeatureItem("跨学科协作", "连接不同学科与研究工具，在团队协作中形成完整的研究能力。"),
                new AboutFeatureItem("项目制人才培养", "以竞赛和科研项目贯穿学习路径，让成员在实践、复盘和公开成果中持续成长。")
        );
    }

    public static List<HeroModelItem> defaultHeroModels() {
        return List.of(
                new HeroModelItem("Unitree Go2", "四足机器人 · 具身智能研究平台", "/models/go2.glb", true),
                new HeroModelItem("Skydio X2", "自主飞行 · 空中感知平台", "/models/skydio-x2.glb", true)
        );
    }
}
