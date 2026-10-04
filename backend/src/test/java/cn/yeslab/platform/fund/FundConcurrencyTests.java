package cn.yeslab.platform.fund;
import com.jayway.jsonpath.JsonPath;
import cn.yeslab.platform.fund.model.FundAccountEntity;
import cn.yeslab.platform.fund.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fund-concurrency;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FundConcurrencyTests {
    @Autowired WebApplicationContext context;
    @Autowired FundAccountRepository fundAccounts;
    @Autowired FundEntryRepository fundEntries;
    @Test void concurrentInitializationRetriesAndSpendingRemainAtomic() throws Exception { verifyConcurrency(false); }
    @Test void seededMigrationAccountUsesSameIdempotencyRules() throws Exception { verifyConcurrency(true); }
    void verifyConcurrency(boolean seeded) throws Exception {
        fundEntries.deleteAll(); fundAccounts.deleteAll();
        if (seeded) fundAccounts.saveAndFlush(new FundAccountEntity());
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String token = JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"teacher\",\"password\":\"YesLab-Teacher-2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.accessToken");
        String opening = payload("OPENING", UUID.randomUUID(), "20.00");
        var initial = concurrent(mvc, token, "/api/v1/fund/initialize", opening, opening);
        assertThat(initial.stream().map(r -> r.getResponse().getStatus())).containsOnly(200);
        assertThat(JsonPath.<String>read(initial.get(0).getResponse().getContentAsString(), "$.data.id"))
                .isEqualTo(JsonPath.read(initial.get(1).getResponse().getContentAsString(), "$.data.id"));
        var expenses = concurrent(mvc, token, "/api/v1/fund/entries", payload("EXPENSE", UUID.randomUUID(), "15.00"), payload("EXPENSE", UUID.randomUUID(), "15.00"));
        assertThat(expenses.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
        mvc.perform(get("/api/v1/fund").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.data.balance").value("5.00"));
        mvc.perform(get("/api/v1/fund/entries").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.data.totalCount").value(2));
    }
    String payload(String type, UUID key, String amount) { return "{\"type\":\"%s\",\"amount\":\"%s\",\"occurredOn\":\"2026-10-03\",\"title\":\"并发测试\",\"requestKey\":\"%s\"}".formatted(type,amount,key); }
    List<MvcResult> concurrent(MockMvc mvc, String token, String path, String first, String second) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<MvcResult>>();
            for (String body : List.of(first, second)) futures.add(executor.submit(() -> {
                ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        }
    }
}
