package cn.openlims.platform.config;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class ValidationConfig {

    @Bean
    ValidationConfigurationCustomizer labDateValidationCustomizer() {
        // LocalDate 的过去/未来校验与业务日期保持一致；Instant 校验仍比较同一时间点。
        Clock labClock = Clock.system(ZoneId.of("Asia/Shanghai"));
        return configuration -> configuration.clockProvider(() -> labClock);
    }
}
