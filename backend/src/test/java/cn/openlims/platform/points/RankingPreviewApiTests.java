package cn.openlims.platform.points;
import cn.openlims.platform.identity.model.*;
import cn.openlims.platform.identity.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.List;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@Transactional
class RankingPreviewApiTests {
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired MemberProfileRepository profiles;
    @Test void publicBoardsAreBoundedAndFullBoardsRequireMembership() throws Exception {
        for (int i = 0; i < 8; i++) {
            var account = accounts.save(new AccountEntity("preview-test-" + i, "unused", Role.MEMBER));
            profiles.save(new MemberProfileEntity(account, "预览测试" + i, "PREVIEW" + i, "软件工程", "1班", "2026", "", MemberStatus.OFFICIAL, List.of("测试")));
        }
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/public/home")).andExpect(status().isOk()).andExpect(jsonPath("$.data.rankingTotalCount", greaterThanOrEqualTo(8)));
        for (String board : List.of("总榜", "月榜", "年榜")) mvc.perform(get("/api/v1/public/rankings").param("board", board))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(6));
        mvc.perform(get("/api/v1/points/preview")).andExpect(status().isUnauthorized());
        String token = JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"member\",\"password\":\"OpenLIMS-Member-2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.accessToken");
        mvc.perform(get("/api/v1/points/preview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entries.length()", greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.data.entries[0].memberCode").doesNotExist());
        accounts.findByUsernameIgnoreCase("member").orElseThrow().setRole(Role.VISITOR);
        mvc.perform(get("/api/v1/points/preview").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }
}
