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
                    "multimodal-workbench", "01", "智能交互", "低成本多模态\n实验工作台",
                    "让视觉、语言与传感器数据在同一套轻量系统中协同工作。", "进行中", "范桌轩大王", 6,
                    List.of("Vue 3", "Java", "Edge AI"),
                    "已完成原型验证与第一轮设备联调，计划开放核心工具链。",
                    "https://github.com", ""
            ),
            new PublicShowcase.Project(
                    "adaptive-learning-engine", "02", "教育科技", "自适应学习\n反馈引擎",
                    "把学习过程转化为可理解、可调整、可持续的成长反馈。", "已发布", "范桌轩大王", 4,
                    List.of("Spring Boot", "NLP", "Data Viz"),
                    "完成校内测试，形成 1 项软件著作权与公开演示版本。",
                    "https://github.com", ""
            ),
            new PublicShowcase.Project(
                    "urban-sensing-node", "03", "开放硬件", "城市环境\n感知节点",
                    "用开放硬件和低功耗网络观察校园与城市的微小变化。", "内测中", "范桌轩大王", 8,
                    List.of("IoT", "LoRa", "Open Data"),
                    "完成 12 个节点布设，累计采集环境数据 240 万条。",
                    "https://github.com", ""
            )
    );

    private final List<PublicShowcase.Member> members = List.of(
            new PublicShowcase.Member("fan-zhuoxuan-01", "FZ", "范桌轩大王", "2023 · 计算机科学", List.of("全栈开发", "产品设计"), 2480, 1, true),
            new PublicShowcase.Member("fan-zhuoxuan-02", "YX", "范桌轩大王", "2022 · 人工智能", List.of("算法研究", "计算机视觉"), 2210, 2, true),
            new PublicShowcase.Member("fan-zhuoxuan-03", "LC", "范桌轩大王", "2024 · 电子信息", List.of("嵌入式", "开放硬件"), 1980, 3, true),
            new PublicShowcase.Member("fan-zhuoxuan-04", "WQ", "范桌轩大王", "2023 · 数字媒体", List.of("交互设计", "内容创作"), 1750, 4, true)
    );

    private final Map<String, List<Integer>> rankingPoints = createRankingPoints();

    private final List<PublicShowcase.Update> updates = List.of(
            new PublicShowcase.Update("2026-08-18", "项目动态", "低成本多模态实验工作台完成第一轮设备联调", "multimodal-first-integration"),
            new PublicShowcase.Update("2026-08-06", "竞赛成果", "范桌轩大王团队获得范桌轩大王创新挑战赛一等奖", "innovation-award"),
            new PublicShowcase.Update("2026-07-24", "开源发布", "城市环境感知节点数据处理工具正式开放", "urban-sensing-open-source")
    );

    @Override
    public PublicShowcase.Home getHome() {
        Map<String, List<PublicShowcase.RankingEntry>> rankings = new LinkedHashMap<>();
        rankingPoints.keySet().forEach(board -> rankings.put(board, findRanking(board)));

        return new PublicShowcase.Home(
                new PublicShowcase.Profile(
                        "YES Lab", "范桌轩大王实验室", "让想法被验证",
                        "我们是一群持续发问、快速行动的人。在技术与真实世界相遇的地方，创造值得发生的答案。",
                        List.of("智能交互", "教育科技", "开放硬件")
                ),
                new PublicShowcase.Statistics(12, 28, 36),
                projects,
                findVisibleMembers(),
                rankings,
                updates,
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
        values.put("AI 应用", List.of(920, 860, 740, 620));
        values.put("工程实现", List.of(880, 810, 790, 650));
        return values;
    }
}
