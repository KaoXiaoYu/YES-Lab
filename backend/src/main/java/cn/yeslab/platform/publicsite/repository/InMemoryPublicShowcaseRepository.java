package cn.yeslab.platform.publicsite.repository;

import cn.yeslab.platform.publicsite.model.PublicShowcase;
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
                    "面向复杂环境，探索飞行平台的自主感知、定位、规划与控制。", "研究中", "范桌轩大王", 0,
                    List.of("自主飞行", "环境感知", "运动规划"),
                    "研究方向建设中，后续将公开阶段性原型、比赛记录与技术文档。",
                    "https://github.com", ""
            ),
            new PublicShowcase.Project(
                    "air-ground-collaboration", "02", "空地协同", "无人机 × 机器狗\n空地协同系统",
                    "连接空中视野与地面行动能力，研究多智能体协同感知和任务执行。", "重点方向", "范桌轩大王", 0,
                    List.of("协同感知", "任务分配", "异构机器人"),
                    "围绕无人机与机器狗协同开展系统设计、算法验证与工程实践。",
                    "https://github.com", ""
            ),
            new PublicShowcase.Project(
                    "embodied-intelligence-platform", "03", "具身智能", "具身智能\n学习与实践平台",
                    "让智能体在真实环境中感知、理解并行动，推动算法走出屏幕。", "方向建设", "范桌轩大王", 0,
                    List.of("多模态感知", "智能决策", "机器人学习"),
                    "面向校内学生建设从基础训练到项目实战的人才培养路径。",
                    "https://github.com", ""
            )
    );

    private final List<PublicShowcase.Member> members = List.of(
            new PublicShowcase.Member("fan-zhuoxuan-01", "FZ", "范桌轩大王", "2023 · 计算机科学", List.of("无人机系统", "工程实现"), 2480, 1, true),
            new PublicShowcase.Member("fan-zhuoxuan-02", "YX", "范桌轩大王", "2022 · 人工智能", List.of("计算机视觉", "具身智能"), 2210, 2, true),
            new PublicShowcase.Member("fan-zhuoxuan-03", "LC", "范桌轩大王", "2024 · 电子信息", List.of("嵌入式", "机器人控制"), 1980, 3, true),
            new PublicShowcase.Member("fan-zhuoxuan-04", "WQ", "范桌轩大王", "2023 · 自动化", List.of("多智能体", "系统设计"), 1750, 4, true)
    );

    private final Map<String, List<Integer>> rankingPoints = createRankingPoints();

    private final List<PublicShowcase.Update> updates = List.of(
            new PublicShowcase.Update("荣誉", "竞赛成果", "YES Lab 获得计算机设计大赛全国二等奖", "national-second-prize"),
            new PublicShowcase.Update("方向", "研究动态", "推进无人机与机器狗空地协同系统研究", "air-ground-research"),
            new PublicShowcase.Update("伙伴", "企业支持", "CUAV 成为 YES Lab 企业赞助伙伴", "cuav-sponsorship")
    );

    @Override
    public PublicShowcase.Home getHome() {
        Map<String, List<PublicShowcase.RankingEntry>> rankings = new LinkedHashMap<>();
        rankingPoints.keySet().forEach(board -> rankings.put(board, findRanking(board)));

        return new PublicShowcase.Home(
                new PublicShowcase.Profile(
                        "YES Lab", "YES Lab 实验室", "探索空地协同，培养未来工程人才",
                        "一个面向无人系统与具身智能的初创实验室，以真实项目连接科研、竞赛与人才培养。",
                        List.of("无人机", "空地协同", "具身智能")
                ),
                new PublicShowcase.Statistics(3, 0, 1),
                projects,
                findVisibleMembers(),
                rankings,
                updates,
                List.of(
                        new PublicShowcase.Sponsor(
                                "CUAV", "企业赞助伙伴",
                                "感谢 CUAV 对 YES Lab 无人系统研究、工程实践与人才培养的支持。",
                                List.of("无人机系统", "工程实践", "人才培养")
                        )
                ),
                List.of(
                        new PublicShowcase.ExternalLink("github", "开源仓库", "https://github.com", true),
                        new PublicShowcase.ExternalLink("bilibili", "哔哩哔哩", "", false),
                        new PublicShowcase.ExternalLink("wechat", "微信公众号", "", false),
                        new PublicShowcase.ExternalLink("douyin", "抖音", "", false)
                )
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
        List<Integer> points = rankingPoints.getOrDefault(board, rankingPoints.get("总榜"));
        return java.util.stream.IntStream.range(0, members.size())
                .mapToObj(index -> {
                    PublicShowcase.Member member = members.get(index);
                    return new PublicShowcase.RankingEntry(
                            index + 1, member.slug(), member.name(), member.initials(), member.tags().getFirst(), points.get(index)
                    );
                })
                .toList();
    }

    @Override
    public List<PublicShowcase.Update> findUpdates() {
        return updates;
    }

    private static Map<String, List<Integer>> createRankingPoints() {
        Map<String, List<Integer>> values = new LinkedHashMap<>();
        values.put("总榜", List.of(2480, 2210, 1980, 1750));
        values.put("月榜", List.of(380, 350, 290, 265));
        values.put("年榜", List.of(1240, 1180, 960, 845));
        values.put("无人机", List.of(920, 860, 740, 620));
        values.put("空地协同", List.of(880, 810, 790, 650));
        values.put("具身智能", List.of(850, 820, 760, 690));
        return values;
    }
}
