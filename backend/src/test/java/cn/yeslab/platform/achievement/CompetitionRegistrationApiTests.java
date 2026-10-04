package cn.yeslab.platform.achievement;

import cn.yeslab.platform.identity.model.*;
import cn.yeslab.platform.identity.repository.*;
import cn.yeslab.platform.achievement.model.*;
import cn.yeslab.platform.achievement.repository.CompetitionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class CompetitionRegistrationApiTests {
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired MemberProfileRepository profiles;
    @Autowired CompetitionRepository competitions;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc; String member; String teacher;
    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        member = login("member", "YesLab-Member-2026!"); teacher = login("teacher", "YesLab-Teacher-2026!");
    }
    String login(String user, String pass) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.accessToken");
    }
    String payload(String lifecycle, String result, String participants) {
        return """
                {"name":"所有参赛情况登记测试","level":"SCHOOL","lifecycle":"%s","resultStatus":%s,
                "competitionDate":"2026-10-10","description":"报名及实际参赛情况。","participants":%s}
                """.formatted(lifecycle, result == null ? "null" : "\"" + result + "\"", participants);
    }
    MockMultipartFile proof() throws Exception {
        var out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("registration", "报名.png", "image/png", out.toByteArray());
    }
    MockMultipartFile data(String body) { return new MockMultipartFile("data", "data.json", "application/json", body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    ResultActions create(String body) throws Exception { return mvc.perform(multipart("/api/v1/competitions").file(data(body)).file(proof()).header("Authorization", "Bearer " + member)); }
    @Test void plannedSingleDateAndFinishedWithoutAwardCanBeRegistered() throws Exception {
        create(payload("PLANNED", null, "[]")).andExpect(status().isOk()).andExpect(jsonPath("$.data.hasRegistration").value(true));
        for (String result : List.of("PENDING_RESULT", "NO_AWARD")) {
            String response = create(payload("FINISHED", result, "[]")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.verificationStatus").value("NOT_REQUIRED"))
                    .andExpect(jsonPath("$.data.hasCertificate").value(false)).andExpect(jsonPath("$.data.resultStatus").value(result))
                    .andReturn().getResponse().getContentAsString();
            String id = JsonPath.read(response, "$.data.id");
            mvc.perform(patch("/api/v1/admin/achievements/competitions/{id}/review", id).header("Authorization", "Bearer " + teacher)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/public/competitions/{id}", id)).andExpect(status().isNotFound());
            String sources = mvc.perform(get("/api/v1/admin/points/sources").header("Authorization", "Bearer " + teacher)).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<List<String>>read(sources, "$.data.competitions[*].id")).doesNotContain(id);
        }
    }
    @Test void requiredProofDateAndStatusCombinationsAreEnforced() throws Exception {
        mvc.perform(multipart("/api/v1/competitions").file(data(payload("PLANNED", null, "[]")))
                .header("Authorization", "Bearer " + member)).andExpect(status().isBadRequest());
        create(payload("PLANNED", null, "[]").replace("\"2026-10-10\"", "null")).andExpect(status().isBadRequest());
        create(payload("PLANNED", "NO_AWARD", "[]")).andExpect(status().isBadRequest());
        create(payload("ONGOING", null, "[]")).andExpect(status().isBadRequest());
        create(payload("FINISHED", null, "[]")).andExpect(status().isBadRequest());
        create(payload("FINISHED", "AWARDED", "[]")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/competitions").file(data(payload("PLANNED", null, "[]")))
                .file(new MockMultipartFile("registration", "fake.png", "image/png", new byte[]{(byte)0x89,'P','N','G',13,10,26,10}))
                .header("Authorization", "Bearer " + member)).andExpect(status().isBadRequest());
    }
    MemberProfileEntity newMember(String user) {
        var account = accounts.save(new AccountEntity(user, encoder.encode("Test-member-2026!"), Role.MEMBER));
        return profiles.saveAndFlush(new MemberProfileEntity(account, user, user, "软件工程", "1班", "2026", "", MemberStatus.OFFICIAL, List.of("测试")));
    }
    @Test void linkedMemberCanReadProofButUnrelatedMemberAndPublicCannot() throws Exception {
        var teammate = newMember("registered-teammate"); newMember("unrelated-teammate");
        String body = payload("PLANNED", null, "[{\"displayName\":\"队员\",\"linkedProfileId\":\"" + teammate.getId() + "\"}]");
        String id = JsonPath.read(create(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
        var teammateToken = login("registered-teammate", "Test-member-2026!");
        var unrelatedToken = login("unrelated-teammate", "Test-member-2026!");
        mvc.perform(get("/api/v1/competitions/{id}/registration", id).header("Authorization", "Bearer " + teammateToken))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/competitions/{id}/registration", id).header("Authorization", "Bearer " + unrelatedToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/competitions/{id}/registration", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/competitions/{id}/registration", id)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/competitions/{id}/registration", id).file(proof()).with(r -> { r.setMethod("PUT"); return r; })
                .header("Authorization", "Bearer " + teammateToken)).andExpect(status().isForbidden());
    }
    @Test void awardedResultRequiresReviewAndEditingResetsApproval() throws Exception {
        var certificate = new MockMultipartFile("certificate", "award.pdf", "application/pdf", "%PDF-1.7 test".getBytes());
        String body = payload("FINISHED", "AWARDED", "[]").replace("\"description\":", "\"awardName\":\"一等奖\",\"description\":");
        String id = JsonPath.read(mvc.perform(multipart("/api/v1/competitions").file(data(body)).file(proof()).file(certificate)
                .header("Authorization", "Bearer " + member)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
        mvc.perform(patch("/api/v1/admin/achievements/competitions/{id}/review", id).header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/competitions/{id}", id).header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("所有参赛情况登记测试", "关键名称已更正")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));
        mvc.perform(get("/api/v1/public/competitions/{id}", id)).andExpect(status().isNotFound());
    }
    @Test void historicalOngoingIsReadableWithoutMutatingLegacyState() throws Exception {
        var captain = profiles.findAll().stream().filter(p -> p.getAccount().getUsername().equals("member")).findFirst().orElseThrow();
        var item = competitions.saveAndFlush(new CompetitionEntity("历史进行中", CompetitionLevel.SCHOOL, CompetitionLifecycle.ONGOING, "历史内容", captain, captain.getAccount()));
        mvc.perform(get("/api/v1/competitions/{id}", item.getId()).header("Authorization", "Bearer " + member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lifecycle").value("ONGOING")).andExpect(jsonPath("$.data.hasRegistration").value(false));
        assertThat(competitions.findById(item.getId()).orElseThrow().getLifecycle()).isEqualTo(CompetitionLifecycle.ONGOING);
        mvc.perform(put("/api/v1/competitions/{id}", item.getId()).header("Authorization", "Bearer " + member)
                .contentType(MediaType.APPLICATION_JSON).content(payload("PLANNED", null, "[]"))).andExpect(status().isBadRequest());
    }
}
