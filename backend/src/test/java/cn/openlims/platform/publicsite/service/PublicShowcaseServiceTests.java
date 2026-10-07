package cn.openlims.platform.publicsite.service;

import cn.openlims.platform.publicsite.repository.InMemoryPublicShowcaseRepository;
import cn.openlims.platform.config.LabBrand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicShowcaseServiceTests {

    private final PublicShowcaseService service = new PublicShowcaseService(new InMemoryPublicShowcaseRepository());

    @Test
    void returnsOnlyPublicShowcaseData() {
        var home = service.getHome();

        assertThat(home.profile().name()).isEqualTo(LabBrand.NAME);
        assertThat(home.profile().fullName()).isEqualTo(LabBrand.FULL_NAME);
        assertThat(home.advisor().name()).isEqualTo("示例导师");
        assertThat(home.projects()).hasSize(3);
        assertThat(home.members()).allMatch(member -> member.visible());
        assertThat(home.members()).filteredOn(member -> member.core()).hasSize(3);
        assertThat(home.rankings()).containsOnlyKeys("总榜", "月榜", "年榜");
        assertThat(home.profile().researchDirections()).containsExactly("无人机", "空地协同", "具身智能");
        assertThat(home.sponsors()).isEmpty();
        assertThat(home.awards()).isEmpty();
        assertThat(home.updates()).isEmpty();
        assertThat(home.statistics().achievements()).isZero();
    }

    @Test
    void returnsProjectByStableSlug() {
        assertThat(service.getProject("air-ground-collaboration").status()).isEqualTo("重点方向");
    }
}
