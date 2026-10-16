package cn.yeslab.platform.recruitment.form;

import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.recruitment.form.repository.RecruitmentFormVersionRepository;
import cn.yeslab.platform.recruitment.form.service.RecruitmentFormService;
import cn.yeslab.platform.recruitment.model.RecruitmentApplicationEntity;
import cn.yeslab.platform.recruitment.repository.RecruitmentApplicationRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class RecruitmentFormApiTests {
    private static final String ADMIN = "/api/v1/admin/recruitment/form";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecruitmentFormVersionRepository versions;
    @Autowired private RecruitmentApplicationRepository applications;
    @Autowired private AccountRepository accounts;
    @Autowired private RecruitmentFormService service;
    private MockMvc mvc;
    private String teacher;

    @BeforeEach void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        teacher = login("teacher", "YesLab-Teacher-2026!");
    }

    @Test void defaultPublicationIsSeededOnceAndMatchesTheCurrentForm() throws Exception {
        String response = admin();
        mvc.perform(get(ADMIN).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.draftRevision").value(0))
                .andExpect(jsonPath("$.data.draft.modules.length()").value(10))
                .andExpect(jsonPath("$.data.published.versionNumber").value(1))
                .andExpect(jsonPath("$.data.published.publishedBy").value("SYSTEM"))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false))
                .andExpect(jsonPath("$.data.draft.modules[9].enabled").value(true));
        long before = versions.count();
        service.initialize();
        assertThat(versions.count()).isEqualTo(before);
        assertThat((String) JsonPath.read(admin(), "$.data.published.id"))
                .isEqualTo((String) JsonPath.read(response, "$.data.published.id"));
    }

    @Test void anonymousMembersAndVisitorsCannotManageTemplates() throws Exception {
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        String member = login("member", "YesLab-Member-2026!");
        String visitor = visitor("form-permission");
        for (String token : List.of(member, visitor)) {
            mvc.perform(get(ADMIN).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(put(ADMIN + "/draft").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(draftBody(defaultDraft(), 0))).andExpect(status().isForbidden());
            mvc.perform(post(ADMIN + "/publish").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedRevision\":0}")).andExpect(status().isForbidden());
        }
        String core = login("core", "YesLab-Core-2026!");
        mvc.perform(get(ADMIN).header("Authorization", core)).andExpect(status().isOk());
    }

    @Test void savingDoesNotChangePublishedContentAndPublishingPreservesHistory() throws Exception {
        String initial = admin();
        String initialId = JsonPath.read(initial, "$.data.published.id");
        var schema = defaultDraft(); schema.put("title", "第一轮模块表单");
        save(schema, 0);
        mvc.perform(get(ADMIN).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.draft.title").value("第一轮模块表单"))
                .andExpect(jsonPath("$.data.draftRevision").value(1))
                .andExpect(jsonPath("$.data.published.id").value(initialId))
                .andExpect(jsonPath("$.data.published.schema.title").value("招新报名表"))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true));
        String first = publish(1); String again = publish(1);
        assertThat((String) JsonPath.read(first, "$.data.id")).isEqualTo((String) JsonPath.read(again, "$.data.id"));
        assertThat((Integer) JsonPath.read(first, "$.data.versionNumber")).isEqualTo(2);
        mvc.perform(get(ADMIN + "/versions/" + initialId).header("Authorization", teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.schema.title").value("招新报名表"));
        mvc.perform(get(ADMIN).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false));
        assertThat(versions.count()).isEqualTo(2);
    }

    @Test void outdatedDraftAndPublishRequestsCannotOverwriteNewerChanges() throws Exception {
        var schema = defaultDraft(); schema.put("title", "保存后的草稿"); save(schema, 0);
        schema.put("title", "过时的修改");
        mvc.perform(put(ADMIN + "/draft").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(schema, 0))).andExpect(status().isConflict());
        mvc.perform(post(ADMIN + "/publish").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}")).andExpect(status().isConflict());
        mvc.perform(get(ADMIN).header("Authorization", teacher))
                .andExpect(jsonPath("$.data.draft.title").value("保存后的草稿"))
                .andExpect(jsonPath("$.data.published.versionNumber").value(1));
    }

    @Test void missingRevisionAndCoreFieldsAreRejectedWithFieldErrors() throws Exception {
        mvc.perform(post(ADMIN + "/publish").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.expectedRevision").exists());
        var schema = defaultDraft();
        @SuppressWarnings("unchecked") var modules = (List<Map<String, Object>>) schema.get("modules");
        @SuppressWarnings("unchecked") var fields = (List<Map<String, Object>>) modules.getFirst().get("fields");
        fields.getFirst().put("required", false);
        mvc.perform(put(ADMIN + "/draft").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(schema, 0))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields").isNotEmpty());
        mvc.perform(get(ADMIN).header("Authorization", teacher)).andExpect(jsonPath("$.data.draftRevision").value(0));
    }

    @Test void repeatedUploadsAndOptionalTechnicalModuleCanBeConfigured() throws Exception {
        var schema = defaultDraft();
        @SuppressWarnings("unchecked") var modules = (List<Map<String, Object>>) schema.get("modules");
        modules.get(9).put("enabled", false);
        @SuppressWarnings("unchecked") Map<String, Object> upload = mapper.readValue(mapper.writeValueAsString(modules.get(8)), Map.class);
        upload.put("id", UUID.randomUUID().toString()); upload.put("binding", null);
        upload.put("title", "项目报告"); upload.put("displayOrder", 10);
        @SuppressWarnings("unchecked") var settings = (Map<String, Object>) upload.get("upload");
        settings.put("allowedTypes", List.of("IMAGE", "PDF")); modules.add(upload);
        save(schema, 0); String result = publish(1);
        assertThat((Boolean) JsonPath.read(result, "$.data.schema.modules[9].enabled")).isFalse();
        assertThat((Integer) JsonPath.read(result, "$.data.schema.modules.length()")).isEqualTo(11);
        assertThat((List<String>) JsonPath.read(result, "$.data.schema.modules[10].upload.allowedTypes")).containsExactly("IMAGE", "PDF");
    }

    @Test void identicalDraftDoesNotCreateAnExtraPublication() throws Exception {
        String before = JsonPath.read(admin(), "$.data.published.id");
        save(defaultDraft(), 0);
        assertThat((String) JsonPath.read(publish(1), "$.data.id")).isEqualTo(before);
        assertThat(versions.count()).isEqualTo(1);
    }

    @Test void serializedConfigurationIsBoundedInBytesBeforeItIsPersisted() throws Exception {
        var schema = defaultDraft();
        @SuppressWarnings("unchecked") var original = (List<Map<String, Object>>) schema.get("modules");
        var modules = new java.util.ArrayList<Map<String, Object>>();
        modules.add(original.getFirst());
        for (int i = 1; i < 50; i++) {
            modules.add(Map.of("id", UUID.randomUUID().toString(), "type", "INFO", "title", "填写说明 " + i,
                    "description", "界".repeat(3000), "required", false, "enabled", true, "displayOrder", i));
        }
        schema.put("modules", modules);
        mvc.perform(put(ADMIN + "/draft").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(schema, 0))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.schema").exists());
        mvc.perform(get(ADMIN).header("Authorization", teacher)).andExpect(jsonPath("$.data.draftRevision").value(0));
    }

    @Test void newApplicantSeesPublicationWhileLegacyAndPinnedApplicationsKeepTheirOwnForm() throws Exception {
        String visitor = visitor("form-version");
        String initialId = JsonPath.read(admin(), "$.data.published.id");
        mvc.perform(get("/api/v1/recruitment/me/form").header("Authorization", visitor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.legacy").value(false))
                .andExpect(jsonPath("$.data.form.id").value(initialId))
                .andExpect(jsonPath("$.data.form.publishedBy").doesNotExist());
        var account = accounts.findByUsernameIgnoreCase("form-version@example.org").orElseThrow();
        var application = new RecruitmentApplicationEntity(account, "报名者", "计算机", "计科 1 班", "2026", "form@example.org",
                List.of("算法"), List.of(), null, List.of("软件"));
        applications.saveAndFlush(application);
        mvc.perform(get("/api/v1/recruitment/me/form").header("Authorization", visitor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.legacy").value(true))
                .andExpect(jsonPath("$.data.form").isEmpty());
        ReflectionTestUtils.setField(application, "formVersionId", UUID.fromString(initialId));
        applications.saveAndFlush(application);
        var schema = defaultDraft(); schema.put("title", "后续报名表"); save(schema, 0); publish(1);
        mvc.perform(get("/api/v1/recruitment/me/form").header("Authorization", visitor))
                .andExpect(jsonPath("$.data.form.id").value(initialId))
                .andExpect(jsonPath("$.data.form.schema.title").value("招新报名表"));
        assertThat(application.getFormAnswersJson()).isNull();
        assertThat(application.getFormAnswersRevision()).isZero();
    }

    private String admin() throws Exception {
        return mvc.perform(get(ADMIN).header("Authorization", teacher)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
    @SuppressWarnings("unchecked") private Map<String, Object> defaultDraft() throws Exception {
        return mapper.readValue(mapper.writeValueAsString(JsonPath.read(admin(), "$.data.draft")), Map.class);
    }
    private String draftBody(Map<String, Object> schema, long revision) {
        return mapper.writeValueAsString(Map.of("expectedRevision", revision, "schema", schema));
    }
    private void save(Map<String, Object> schema, long revision) throws Exception {
        mvc.perform(put(ADMIN + "/draft").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(schema, revision))).andExpect(status().isOk());
    }
    private String publish(long revision) throws Exception {
        return mvc.perform(post(ADMIN + "/publish").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":" + revision + "}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
    private String login(String name, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", name, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.data.accessToken");
    }
    private String visitor(String name) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", name + "@example.org", "password", "Visitor-2026!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.data.accessToken");
    }
}
