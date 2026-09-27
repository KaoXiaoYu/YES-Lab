package cn.yeslab.platform.notification;

import cn.yeslab.platform.identity.repository.AccountRepository;
import cn.yeslab.platform.notification.service.NotificationService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class NotificationMascotApiTests {
    private static final String SETTINGS = "/api/v1/admin/notifications/melina-visibility";
    private static final String ALL_ROLES = "[\"TEACHER\",\"CORE_STUDENT\",\"MEMBER\",\"VISITOR\"]";
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private NotificationService notifications;
    @Autowired private EntityManager entityManager;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void teachersChoosePerAccountAndNamesFollowRecipientWithoutChangingHistory() throws Exception {
        String teacher = login("teacher", "Teacher");
        String member = login("member", "Member");
        String core = login("core", "Core");
        UUID memberId = accounts.findByUsernameIgnoreCase("member").orElseThrow().getId();
        notifications.send(accounts.findById(memberId).orElseThrow(), "TEST", "审核完成", "请查看结果", "/inbox");
        mvc.perform(get("/api/v1/notifications/visibility").header("Authorization", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mascot").value("MELINA"));
        mvc.perform(put(SETTINGS).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("[{\"accountId\":\"" + memberId + "\",\"mascot\":\"NAILONG\"}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts[?(@.accountId == '" + memberId + "')].mascot", hasItem("NAILONG")));
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/v1/notifications/visibility").header("Authorization", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mascot").value("NAILONG"));
        mvc.perform(get("/api/v1/notifications/visibility").header("Authorization", core))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mascot").value("MELINA"));
        String inbox = mvc.perform(get("/api/v1/notifications").header("Authorization", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mascot").value("NAILONG"))
                .andExpect(jsonPath("$.data.messages[0].senderName").value("奶龙"))
                .andReturn().getResponse().getContentAsString();
        String messageId = JsonPath.read(inbox, "$.data.messages[0].id");

        // An old client only updating visibility must preserve the chosen character.
        mvc.perform(put(SETTINGS).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibleRoles\":" + ALL_ROLES + ",\"overrides\":[{\"accountId\":\"" + memberId + "\",\"visible\":false}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/visibility").header("Authorization", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.visible").value(false))
                .andExpect(jsonPath("$.data.mascot").value("NAILONG"));
        mvc.perform(put(SETTINGS).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("[]"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications").header("Authorization", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mascot").value("MELINA"))
                .andExpect(jsonPath("$.data.messages[0].senderName").value("梅琳娜"))
                .andExpect(jsonPath("$.data.messages[0].id").value(messageId));
    }

    @Test
    void onlyTeachersCanReadOrWriteMascotSettings() throws Exception {
        String visitorResponse = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mascot-" + UUID.randomUUID().toString().substring(0, 8)
                                + "@example.com\",\"password\":\"Mascot-2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String visitor = "Bearer " + JsonPath.read(visitorResponse, "$.data.accessToken");
        UUID memberId = accounts.findByUsernameIgnoreCase("member").orElseThrow().getId();
        for (String token : List.of(login("core", "Core"), login("member", "Member"), visitor)) {
            mvc.perform(get(SETTINGS).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(put(SETTINGS).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                            .content(payload("[{\"accountId\":\"" + memberId + "\",\"mascot\":\"NAILONG\"}]")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(payload("[]")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(SETTINGS).header("Authorization", login("teacher", "Teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts[?(@.accountId == '" + memberId + "')].mascot", hasItem("MELINA")));
    }

    @Test
    void invalidSelectionsAreRejectedAtomicallyAndDisabledPreferencesArePreserved() throws Exception {
        String teacher = login("teacher", "Teacher");
        UUID memberId = accounts.findByUsernameIgnoreCase("member").orElseThrow().getId();
        String choice = "{\"accountId\":\"" + memberId + "\",\"mascot\":\"NAILONG\"}";
        mvc.perform(put(SETTINGS).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("[" + choice + "]"))).andExpect(status().isOk());
        for (String invalid : List.of("[" + choice + "," + choice + "]", "[null]",
                "[{\"accountId\":\"" + UUID.randomUUID() + "\",\"mascot\":\"NAILONG\"}]",
                "[{\"accountId\":\"" + memberId + "\",\"mascot\":\"UNKNOWN\"}]")) {
            mvc.perform(put(SETTINGS).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                            .content(payload(invalid).replace(ALL_ROLES, "[]")))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(SETTINGS).header("Authorization", teacher)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.visibleRoles", hasItem("MEMBER")))
                    .andExpect(jsonPath("$.data.accounts[?(@.accountId == '" + memberId + "')].mascot", hasItem("NAILONG")));
        }
        var member = accounts.findById(memberId).orElseThrow();
        member.setEnabled(false);
        accounts.saveAndFlush(member);
        mvc.perform(put(SETTINGS).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("[" + choice + "]"))).andExpect(status().isBadRequest());
        mvc.perform(put(SETTINGS).header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("[]"))).andExpect(status().isOk());
        member.setEnabled(true);
        accounts.saveAndFlush(member);
        mvc.perform(get(SETTINGS).header("Authorization", teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts[?(@.accountId == '" + memberId + "')].mascot", hasItem("NAILONG")));
    }

    private String payload(String choices) {
        return "{\"visibleRoles\":" + ALL_ROLES + ",\"overrides\":[],\"mascotOverrides\":" + choices + "}";
    }

    private String login(String username, String passwordRole) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"YesLab-" + passwordRole + "-2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.data.accessToken");
    }
}
