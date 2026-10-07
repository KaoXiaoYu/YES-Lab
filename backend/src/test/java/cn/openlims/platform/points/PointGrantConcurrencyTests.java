package cn.openlims.platform.points;

import cn.openlims.platform.identity.repository.MemberProfileRepository;
import cn.openlims.platform.points.repository.PointGrantRepository;
import cn.openlims.platform.notification.repository.NotificationRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:points-concurrency;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PointGrantConcurrencyTests {
    @Autowired WebApplicationContext context;
    @Autowired PointGrantRepository grants;
    @Autowired MemberProfileRepository profiles;
    @Autowired NotificationRepository notifications;

    @Test
    void simultaneousRetriesCreditAndNotifyOnlyOnce() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"teacher\",\"password\":\"OpenLIMS-Teacher-2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.data.accessToken");
        var member = profiles.findAll().stream().filter(p -> p.getName().equals("示例成员")).findFirst().orElseThrow();
        UUID memberId = member.getId();
        int before = member.getTotalPoints();
        long notificationCount = notifications.count();
        UUID key = UUID.randomUUID();
        String body = """
                {"title":"并发积分重试测试","subcategory":"MEDIA_CONTENT","occurredOn":"%s",
                 "itemTotalPoints":25,"requestKey":"%s",
                 "allocations":[{"memberProfileId":"%s","points":25,"contribution":"制作内容"}]}
                """.formatted(LocalDate.now(ZoneId.of("Asia/Shanghai")), key, memberId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<String> submit = () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post("/api/v1/admin/points/grants").header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            };
            Future<String> first = executor.submit(submit);
            Future<String> second = executor.submit(submit);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            String firstId = JsonPath.read(first.get(30, TimeUnit.SECONDS), "$.data.id");
            String secondId = JsonPath.read(second.get(30, TimeUnit.SECONDS), "$.data.id");
            assertThat(firstId).isEqualTo(secondId);
            assertThat(grants.findByRequestKey(key)).isPresent();
            assertThat(profiles.findById(memberId).orElseThrow().getTotalPoints()).isEqualTo(before + 25);
            assertThat(notifications.count()).isEqualTo(notificationCount + 1);
        }
    }
}
