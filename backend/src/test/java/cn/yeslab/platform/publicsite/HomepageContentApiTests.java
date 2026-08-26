package cn.yeslab.platform.publicsite;

import cn.yeslab.platform.publicsite.cms.api.HomepageModels;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class HomepageContentApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void onlySystemAdminsCanMaintainHomepageAndPublicHomeUsesSavedContent() throws Exception {
        String teacherToken = login("teacher", "YesLab-Teacher-2026!");
        String coreToken = login("core", "YesLab-Core-2026!");
        String memberToken = login("member", "YesLab-Member-2026!");

        mvc.perform(get("/api/v1/admin/homepage").header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden());

        HomepageModels.HomepageContent defaults = HomepageModels.defaultContent();
        HomepageModels.ProfileContent profile = defaults.profile();
        HomepageModels.ProfileContent editedProfile = new HomepageModels.ProfileContent(
                profile.name(), "管理员可维护的首页", profile.fullName(), profile.slogan(), profile.description(),
                profile.researchDirections(), List.of(
                        new HomepageModels.ResearchDirectionItem("无人机", "#projects"),
                        new HomepageModels.ResearchDirectionItem("具身智能", "https://example.com/research")
                ), profile.heroEyebrow(), "内容管理测试", profile.heroAccent(),
                profile.primaryActionLabel(), profile.secondaryActionLabel()
        );
        HomepageModels.HomepageContent edited = new HomepageModels.HomepageContent(
                editedProfile, defaults.sections(), defaults.proofItems(), defaults.updates(), defaults.awards(),
                defaults.sponsors(), defaults.externalLinks(), defaults.advisorProfileId(),
                defaults.featuredMemberProfileIds(), defaults.featuredProjectIds()
        );

        mvc.perform(put("/api/v1/admin/homepage")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.profile.displayName").value("管理员可维护的首页"))
                .andExpect(jsonPath("$.data.content.profile.researchDirectionItems[1].url").value("https://example.com/research"))
                .andExpect(jsonPath("$.data.updatedBy").value("teacher"));

        HomepageModels.ProfileContent unsafeProfile = new HomepageModels.ProfileContent(
                profile.name(), profile.displayName(), profile.fullName(), profile.slogan(), profile.description(),
                profile.researchDirections(), List.of(new HomepageModels.ResearchDirectionItem("无人机", "javascript:alert(1)")),
                profile.heroEyebrow(), profile.heroTitle(), profile.heroAccent(),
                profile.primaryActionLabel(), profile.secondaryActionLabel()
        );
        HomepageModels.HomepageContent unsafe = new HomepageModels.HomepageContent(
                unsafeProfile, defaults.sections(), defaults.proofItems(), defaults.updates(), defaults.awards(),
                defaults.sponsors(), defaults.externalLinks(), defaults.advisorProfileId(),
                defaults.featuredMemberProfileIds(), defaults.featuredProjectIds()
        );
        mvc.perform(put("/api/v1/admin/homepage")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unsafe)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/admin/homepage").header("Authorization", bearer(coreToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.profile.heroTitle").value("内容管理测试"));

        mvc.perform(get("/api/v1/public/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.displayName").value("管理员可维护的首页"))
                .andExpect(jsonPath("$.data.profile.researchDirections[1]").value("具身智能"))
                .andExpect(jsonPath("$.data.homepageContent.profile.heroTitle").value("内容管理测试"));
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
