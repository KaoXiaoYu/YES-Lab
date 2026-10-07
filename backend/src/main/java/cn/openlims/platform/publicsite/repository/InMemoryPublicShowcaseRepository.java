package cn.openlims.platform.publicsite.repository;

import cn.openlims.platform.config.LabBrand;

import cn.openlims.platform.publicsite.model.PublicShowcase;
import cn.openlims.platform.publicsite.cms.api.HomepageModels;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryPublicShowcaseRepository implements PublicShowcaseRepository {

    private final List<PublicShowcase.Project> projects = List.of(
            new PublicShowcase.Project(
                    "uav-autonomous-flight", "01", "无人机", "无人机自主飞行\n与环境感知",
                    "面向复杂环境，探索飞行平台的自主感知、定位、规划与控制。", "研究中", "示例成员", 0,
                    List.of("自主飞行", "环境感知", "运动规划"),
                    "研究方向建设中，后续将公开阶段性原型、比赛记录与技术文档。",
                    "https://github.com", ""
            ),
            new PublicShowcase.Project(
                    "air-ground-collaboration", "02", "空地协同", "无人机 × 机器狗\n空地协同系统",
                    "连接空中视野与地面行动能力，研究多智能体协同感知和任务执行。", "重点方向", "示例成员", 0,
                    List.of("协同感知", "任务分配", "异构机器人"),
                    "围绕无人机与机器狗协同开展系统设计、算法验证与工程实践。",
                    "https://github.com", ""
            ),
            new PublicShowcase.Project(
                    "embodied-intelligence-platform", "03", "具身智能", "具身智能\n学习与实践平台",
                    "让智能体在真实环境中感知、理解并行动，推动算法走出屏幕。", "方向建设", "示例成员", 0,
                    List.of("多模态感知", "智能决策", "机器人学习"),
                    "面向校内学生建设从基础训练到项目实战的人才培养路径。",
                    "https://github.com", ""
            )
    );

    private final List<PublicShowcase.Member> members = List.of(
            new PublicShowcase.Member("fan-zhuoxuan-01", "FZ", "示例成员", "2023 · 计算机科学", List.of("无人机系统", "工程实现"), 2480, 1, true, true),
            new PublicShowcase.Member("fan-zhuoxuan-02", "YX", "示例成员", "2022 · 人工智能", List.of("计算机视觉", "具身智能"), 2210, 2, true, true),
            new PublicShowcase.Member("fan-zhuoxuan-03", "LC", "示例成员", "2024 · 电子信息", List.of("嵌入式", "机器人控制"), 1980, 3, true, true),
            new PublicShowcase.Member("fan-zhuoxuan-04", "WQ", "示例成员", "2023 · 自动化", List.of("多智能体", "系统设计"), 1750, 4, false, true)
    );

    private final Map<String, List<Integer>> rankingPoints = createRankingPoints();

    private final List<PublicShowcase.Update> updates = List.of();

    private final List<PublicShowcase.Award> awards = List.of();

    @Override
    public PublicShowcase.Home getHome() {
        Map<String, List<PublicShowcase.RankingEntry>> rankings = new LinkedHashMap<>();
        rankingPoints.keySet().forEach(board -> rankings.put(board, findRanking(board)));

        return new PublicShowcase.Home(
                new PublicShowcase.Profile(
                        LabBrand.NAME, LabBrand.DISPLAY_NAME, LabBrand.FULL_NAME, "连接研究、协作与成长",
                        LabBrand.DESCRIPTION,
                        List.of("无人机", "空地协同", "具身智能")
                ),
                new PublicShowcase.Advisor(
                        "TH", "示例导师", LabBrand.NAME + " 指导老师",
                        "负责实验室研究方向、项目实践与人才培养指导。",
                        List.of("研究指导", "人才培养")
                ),
                new PublicShowcase.Statistics(3, 0, awards.size()),
                projects,
                findVisibleMembers(),
                rankings,
                0,
                updates,
                awards,
                List.of(),
                List.of(
                        new PublicShowcase.ExternalLink("github", "开源仓库", LabBrand.REPOSITORY_URL, true),
                        new PublicShowcase.ExternalLink("bilibili", "哔哩哔哩", "", false),
                        new PublicShowcase.ExternalLink("wechat", "微信公众号", "", false),
                        new PublicShowcase.ExternalLink("douyin", "抖音", "", false)
                ),
                HomepageModels.defaultContent()
        );
    }

    @Override
    public List<PublicShowcase.Project> findProjects() {
        return projects;
    }

    @Override
    public Optional<PublicShowcase.Project> findProjectBySlug(String slug) {
        return projects.stream().filter(project -> project.slug().equals(slug)).findFirst();
    }

    @Override
    public List<PublicShowcase.Member> findVisibleMembers() {
        return members.stream().filter(PublicShowcase.Member::visible).toList();
    }

    @Override
    public Optional<PublicShowcase.Member> findVisibleMemberBySlug(String slug) {
        return members.stream().filter(PublicShowcase.Member::visible).filter(member -> member.slug().equals(slug)).findFirst();
    }

    @Override
    public List<PublicShowcase.RankingEntry> findRanking(String board) {
        return List.of();
    }

    @Override
    public List<PublicShowcase.Update> findUpdates() {
        return updates;
    }

    private static Map<String, List<Integer>> createRankingPoints() {
        Map<String, List<Integer>> values = new LinkedHashMap<>();
        values.put("总榜", List.of());
        values.put("月榜", List.of());
        values.put("年榜", List.of());
        return values;
    }
}
