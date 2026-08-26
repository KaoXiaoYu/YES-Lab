package cn.yeslab.platform.publicsite.cms.api;

import jakarta.validation.Valid;
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
            @Valid @NotNull @Size(max = 6) List<ProofItem> proofItems,
            @Valid @NotNull @Size(max = 20) List<UpdateItem> updates,
            @Valid @NotNull @Size(max = 30) List<AwardItem> awards,
            @Valid @NotNull @Size(max = 20) List<SponsorItem> sponsors,
            @Valid @NotNull @Size(max = 12) List<ExternalLinkItem> externalLinks,
            UUID advisorProfileId,
            @NotNull @Size(max = 12) List<UUID> featuredMemberProfileIds,
            @NotNull @Size(max = 12) List<UUID> featuredProjectIds
    ) {
    }

    public record ProfileContent(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(max = 180) String slogan,
            @NotBlank @Size(max = 5000) String description,
            @NotNull @Size(min = 1, max = 12) List<@NotBlank @Size(max = 80) String> researchDirections,
            @Valid @Size(min = 1, max = 12) List<ResearchDirectionItem> researchDirectionItems,
            @NotBlank @Size(max = 120) String heroEyebrow,
            @NotBlank @Size(max = 180) String heroTitle,
            @NotBlank @Size(max = 80) String heroAccent,
            @NotBlank @Size(max = 60) String primaryActionLabel,
            @NotBlank @Size(max = 60) String secondaryActionLabel
    ) {
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
            @Valid @NotNull @Size(max = 8) List<AboutFeatureItem> features
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
            @NotBlank @Size(max = 160) String detail
    ) {
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
            @NotBlank @Size(max = 800) String websiteUrl
    ) {
    }

    public record ExternalLinkItem(
            @NotBlank @Size(max = 40) String platform,
            @NotBlank @Size(max = 80) String label,
            @Size(max = 800) String url,
            boolean enabled
    ) {
    }

    public record HomepageAdminView(HomepageContent content, Instant updatedAt, String updatedBy) {
    }

    public static HomepageContent defaultContent() {
        return new HomepageContent(
                new ProfileContent(
                        "YES Lab", "YES Lab 实验室", "Yichun Embodied Science", "探索空地协同，培养未来工程人才",
                        "一个面向无人系统与具身智能的初创实验室，以真实项目连接科研、竞赛与人才培养。",
                        List.of("无人机", "空地协同", "具身智能"),
                        List.of(
                                new ResearchDirectionItem("无人机", "#projects"),
                                new ResearchDirectionItem("空地协同", "#projects"),
                                new ResearchDirectionItem("具身智能", "#projects")
                        ),
                        "YES LAB · ROBOTICS RESEARCH / 2026", "让无人设备带上、\n你的", "眼眸", "浏览研究项目", "了解合作伙伴"
                ),
                new PageSections(
                        new SectionCopy("01 / SELECTED RESEARCH", "研究与工程实践", "从算法、硬件到系统集成，我们以可运行、可验证的真实项目建立研究能力。"),
                        new AboutSection(
                                "02 / ABOUT", "把研究做成\n可以触碰的现场",
                                "YES Lab 是一个处于起步阶段的实验室，主要研究无人机、无人机与机器狗空地协同、具身智能等方向。",
                                "我们以竞赛与真实工程项目为牵引，为学校培养兼具算法、硬件和系统能力的复合型人才。",
                                List.of("面向真实场景", "强调应用实践", "培养工程人才"),
                                "用竞赛检验技术，以成果记录成长。",
                                "HOW WE WORK", "从研究方向走向工程现场",
                                defaultAboutFeatures()
                        ),
                        new SectionCopy("03 / PEOPLE", "共同成长的研究者", "榜单每30s刷新"),
                        new SectionCopy("04 / PARTNERS", "赞助与合作伙伴", "感谢企业伙伴为无人系统研究、工程实践与人才培养提供支持。"),
                        new SectionCopy("05 / ACHIEVEMENTS", "成果与外部报道", "新闻按发布日期自动排序"),
                        new ContactSection("06 / CONNECT", "下一次探索，\n从这里开始。", "关注我们的研究、比赛和开源进展。"),
                        "© 2026 YES Lab · INTELLIGENCE IN MOTION"
                ),
                List.of(
                        new ProofItem("01 / AWARDS", "3 项奖项", "全国 / 省赛 / 赛区"),
                        new ProofItem("02 / FOCUS", "3 个方向", "无人系统与具身智能"),
                        new ProofItem("03 / PARTNER", "CUAV", "企业赞助伙伴"),
                        new ProofItem("04 / STATUS", "持续建设", "开放、实践、成长")
                ),
                List.of(
                        new UpdateItem("荣誉", "竞赛成果", "YES Lab 获得计算机设计大赛全国二等奖", "national-second-prize"),
                        new UpdateItem("荣誉", "竞赛成果", "江西省智能机器人大赛飞行巡航定点赛道省赛二等奖", "jiangxi-robot-flight-second-prize"),
                        new UpdateItem("荣誉", "竞赛成果", "全国智能汽车大赛平衡轮腿组华东赛赛区三等奖", "smart-car-east-china-third-prize"),
                        new UpdateItem("方向", "研究动态", "推进无人机与机器狗空地协同系统研究", "air-ground-research"),
                        new UpdateItem("伙伴", "企业支持", "CUAV 成为 YES Lab 企业赞助伙伴", "cuav-sponsorship")
                ),
                List.of(
                        new AwardItem("计算机设计大赛", "全国赛", "全国", "二等奖"),
                        new AwardItem("江西省智能机器人大赛", "飞行巡航定点赛道", "省赛", "二等奖"),
                        new AwardItem("全国智能汽车大赛", "平衡轮腿组 · 华东赛", "赛区", "三等奖")
                ),
                List.of(new SponsorItem(
                        "CUAV", "企业赞助伙伴", "感谢 CUAV 对 YES Lab 无人系统研究、工程实践与人才培养的支持。",
                        List.of("无人机系统", "工程实践", "人才培养"), "/sponsors/cuav-logo.jpg", "https://www.cuav.net/"
                )),
                List.of(
                        new ExternalLinkItem("github", "开源仓库", "https://github.com", true),
                        new ExternalLinkItem("bilibili", "哔哩哔哩", "", false),
                        new ExternalLinkItem("wechat", "微信公众号", "", false),
                        new ExternalLinkItem("douyin", "抖音", "", false)
                ),
                null,
                List.of(),
                List.of()
        );
    }

    public static List<AboutFeatureItem> defaultAboutFeatures() {
        return List.of(
                new AboutFeatureItem("真实问题驱动", "从无人系统的真实任务出发，把研究目标拆解为可以验证的算法、硬件与系统方案。"),
                new AboutFeatureItem("跨平台协同", "连接无人机、机器狗与具身智能平台，在异构系统协同中训练完整工程能力。"),
                new AboutFeatureItem("项目制人才培养", "以竞赛和科研项目贯穿学习路径，让成员在实践、复盘和公开成果中持续成长。")
        );
    }
}
