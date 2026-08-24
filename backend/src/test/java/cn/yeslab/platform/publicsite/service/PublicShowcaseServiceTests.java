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
        assertThat(home.projects()).hasSize(3);
        assertThat(home.members()).allMatch(member -> member.visible());
        assertThat(home.rankings()).containsKeys("总榜", "月榜", "年榜", "AI 应用", "工程实现");
    }

    @Test
    void returnsProjectByStableSlug() {
        assertThat(service.getProject("multimodal-workbench").status()).isEqualTo("进行中");
    }
}
