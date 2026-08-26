package cn.yeslab.platform.publicsite.service;

import cn.yeslab.platform.publicsite.repository.InMemoryPublicShowcaseRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicShowcaseServiceTests {

    private final PublicShowcaseService service = new PublicShowcaseService(new InMemoryPublicShowcaseRepository());

    @Test
    void returnsOnlyPublicShowcaseData() {
        var home = service.getHome();

        assertThat(home.profile().name()).isEqualTo("YES Lab");
        assertThat(home.profile().fullName()).isEqualTo("Yichun Embodied Science");
        assertThat(home.advisor().name()).isEqualTo("汤洪大王");
        assertThat(home.projects()).hasSize(3);
        assertThat(home.members()).allMatch(member -> member.visible());
        assertThat(home.members()).filteredOn(member -> member.core()).hasSize(3);
        assertThat(home.rankings()).containsKeys("总榜", "月榜", "年榜", "无人机", "空地协同", "具身智能");
        assertThat(home.profile().researchDirections()).containsExactly("无人机", "空地协同", "具身智能");
        assertThat(home.sponsors()).extracting("name").containsExactly("CUAV");
        assertThat(home.sponsors().getFirst().logoUrl()).isEqualTo("/sponsors/cuav-logo.jpg");
        assertThat(home.statistics().achievements()).isEqualTo(3);
        assertThat(home.awards()).hasSize(3);
        assertThat(home.awards()).extracting("competition").containsExactly(
                "计算机设计大赛", "江西省智能机器人大赛", "全国智能汽车大赛"
        );
        assertThat(home.awards().get(1).category()).isEqualTo("飞行巡航定点赛道");
        assertThat(home.awards().get(2).prize()).isEqualTo("三等奖");
    }

    @Test
    void returnsProjectByStableSlug() {
        assertThat(service.getProject("air-ground-collaboration").status()).isEqualTo("重点方向");
    }
}
