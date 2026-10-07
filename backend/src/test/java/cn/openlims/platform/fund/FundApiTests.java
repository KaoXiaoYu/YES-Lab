package cn.openlims.platform.fund;

import com.jayway.jsonpath.JsonPath;
import cn.openlims.platform.identity.model.Role;
import cn.openlims.platform.identity.repository.AccountRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class FundApiTests {
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    MockMvc mvc;
    String teacher;
    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        teacher = login("teacher", "OpenLIMS-Teacher-2026!");
    }
    String login(String name, String password) throws Exception {
        String result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(result, "$.data.accessToken");
    }
    String body(String type, String amount, UUID key) {
        return "{\"type\":\"%s\",\"amount\":\"%s\",\"occurredOn\":\"2026-10-03\",\"title\":\"账本测试\",\"description\":\"说明\",\"requestKey\":\"%s\"}".formatted(type, amount, key);
    }
    String entry(String type, String amount) throws Exception {
        var request = post(type.equals("OPENING") ? "/api/v1/fund/initialize" : "/api/v1/fund/entries")
                .header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                .content(body(type, amount, UUID.randomUUID()));
        return JsonPath.read(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.id");
    }
    @Test void unknownBalanceAndMemberPermissionsAreExplicit() throws Exception {
        mvc.perform(get("/api/v1/public/fund/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialized").value(false)).andExpect(jsonPath("$.data.balance").isEmpty());
        mvc.perform(get("/api/v1/fund/entries")).andExpect(status().isUnauthorized());
        var member = login("member", "OpenLIMS-Member-2026!");
        mvc.perform(get("/api/v1/fund").header("Authorization", "Bearer " + member)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/fund/initialize").header("Authorization", "Bearer " + member)
                .contentType(MediaType.APPLICATION_JSON).content(body("OPENING", "0", UUID.randomUUID()))).andExpect(status().isForbidden());
        accounts.findByUsernameIgnoreCase("member").orElseThrow().setRole(Role.VISITOR);
        mvc.perform(get("/api/v1/fund/entries").header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
    }
    @Test void preciseTotalsExcludeReversedEntriesAndOpeningBalance() throws Exception {
        entry("OPENING", "100.00");
        var income = entry("INCOME", "10.10");
        entry("EXPENSE", "0.20");
        mvc.perform(get("/api/v1/public/fund/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value("109.90"))
                .andExpect(jsonPath("$.data.income").value("10.10"))
                .andExpect(jsonPath("$.data.expense").value("0.20"))
                .andExpect(jsonPath("$.data.entries").doesNotExist());
        reverse(income, UUID.randomUUID()).andExpect(status().isOk());
        mvc.perform(get("/api/v1/fund").header("Authorization", "Bearer " + teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value("99.80")).andExpect(jsonPath("$.data.income").value("0.00"));
        mvc.perform(get("/api/v1/fund/entries").header("Authorization", "Bearer " + teacher).param("type", "INCOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entries[0].reversalEntryId").isNotEmpty())
                .andExpect(jsonPath("$.data.entries[0].reason").value("误记纠正"));
        reverse(income, UUID.randomUUID()).andExpect(status().isConflict());
    }
    org.springframework.test.web.servlet.ResultActions reverse(String id, UUID key) throws Exception {
        return mvc.perform(post("/api/v1/fund/entries/{id}/reverse", id).header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"误记纠正\",\"requestKey\":\"" + key + "\"}"));
    }
    @Test void retriesReplayButChangedPayloadConflicts() throws Exception {
        entry("OPENING", "0");
        var key = UUID.randomUUID();
        var payload = body("INCOME", "20.01", key);
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/fund/entries").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/fund/entries").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content(body("INCOME", "20.02", key))).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/fund").header("Authorization", "Bearer " + teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value("20.01"));
    }
    @Test void noOverdraftAndNoNegativeReversal() throws Exception {
        entry("OPENING", "0"); var income = entry("INCOME", "10"); entry("EXPENSE", "8");
        mvc.perform(post("/api/v1/fund/entries").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content(body("EXPENSE", "3", UUID.randomUUID()))).andExpect(status().isConflict());
        reverse(income, UUID.randomUUID()).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/fund").header("Authorization", "Bearer " + teacher)).andExpect(jsonPath("$.data.balance").value("2.00"));
    }
    @Test void amountDateAndPaginationValidationAndOneTimeInitialization() throws Exception {
        entry("OPENING", "0");
        mvc.perform(post("/api/v1/fund/initialize").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                .content(body("OPENING", "1", UUID.randomUUID()))).andExpect(status().isConflict());
        for (String amount : new String[]{"-1", "0", "0.001"}) mvc.perform(post("/api/v1/fund/entries")
                .header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                .content(body("INCOME", amount, UUID.randomUUID()))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/fund/entries").header("Authorization", "Bearer " + teacher).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/fund/entries").header("Authorization", "Bearer " + teacher).param("from", "2026-10-04").param("to", "2026-10-03")).andExpect(status().isBadRequest());
        entry("INCOME", "5"); entry("EXPENSE", "1");
        mvc.perform(get("/api/v1/fund/entries").header("Authorization", "Bearer " + teacher).param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(3)).andExpect(jsonPath("$.data.entries.length()").value(1));
    }
}
