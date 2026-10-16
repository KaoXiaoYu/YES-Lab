package cn.yeslab.platform.recruitment.form;

import cn.yeslab.platform.recruitment.form.repository.RecruitmentFormConfigRepository;
import cn.yeslab.platform.recruitment.form.repository.RecruitmentFormVersionRepository;
import cn.yeslab.platform.recruitment.form.service.RecruitmentFormService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecruitmentFormConcurrencyTests {
    private static final String URL = "/api/v1/admin/recruitment/form";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecruitmentFormService service;
    @Autowired private RecruitmentFormConfigRepository configs;
    @Autowired private RecruitmentFormVersionRepository versions;
    private MockMvc mvc;
    private String token;

    @BeforeEach void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"teacher\",\"password\":\"YesLab-Teacher-2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + JsonPath.read(response, "$.data.accessToken");
    }

    @Test void simultaneousEditorsCannotLoseEachOthersDraft() throws Exception {
        String view = admin();
        long revision = ((Number) JsonPath.read(view, "$.data.draftRevision")).longValue();
        var schema = draft(view); schema.put("title", "并发编辑的草稿");
        String body = mapper.writeValueAsString(Map.of("expectedRevision", revision, "schema", schema));
        var results = race(() -> mvc.perform(put(URL + "/draft").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn());
        assertThat(results.stream().map(result -> result.getResponse().getStatus()).sorted().toList())
                .containsExactly(200, 409);
        assertThat(((Number) JsonPath.read(admin(), "$.data.draftRevision")).longValue()).isEqualTo(revision + 1);
    }

    @Test void simultaneousPublishRequestsCreateOneImmutableSnapshot() throws Exception {
        String view = admin();
        long revision = ((Number) JsonPath.read(view, "$.data.draftRevision")).longValue();
        var schema = draft(view); schema.put("title", "并发发布 " + revision);
        mvc.perform(put(URL + "/draft").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("expectedRevision", revision, "schema", schema))))
                .andExpect(status().isOk());
        long before = versions.count();
        String body = "{\"expectedRevision\":" + (revision + 1) + "}";
        var results = race(() -> mvc.perform(post(URL + "/publish").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn());
        String first = JsonPath.read(results.getFirst().getResponse().getContentAsString(), "$.data.id");
        String second = JsonPath.read(results.getLast().getResponse().getContentAsString(), "$.data.id");
        assertThat(first).isEqualTo(second);
        assertThat(versions.count()).isEqualTo(before + 1);
    }

    @Test void simultaneousInitializersKeepOneCompleteDefaultConfiguration() throws Exception {
        // Only this create-drop test database is cleared; all legacy applications have a null form version.
        configs.deleteAllInBatch(); versions.deleteAllInBatch();
        race(() -> { service.initialize(); return true; });
        assertThat(configs.count()).isEqualTo(1);
        assertThat(versions.count()).isEqualTo(1);
        assertThat(versions.existsById(configs.findById(1L).orElseThrow().getPublishedVersionId())).isTrue();
    }

    private <T> List<T> race(Callable<T> action) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<T> work = () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return action.call();
            };
            var first = pool.submit(work); var second = pool.submit(work);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }
    private String admin() throws Exception {
        return mvc.perform(get(URL).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
    @SuppressWarnings("unchecked") private Map<String, Object> draft(String view) {
        return mapper.readValue(mapper.writeValueAsString(JsonPath.read(view, "$.data.draft")), Map.class);
    }
}
