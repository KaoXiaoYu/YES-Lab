package cn.yeslab.platform.recruitment.form.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(200)
public class RecruitmentFormInitializer implements ApplicationRunner {
    private final RecruitmentFormService service;
    public RecruitmentFormInitializer(RecruitmentFormService service) { this.service = service; }
    @Override public void run(ApplicationArguments args) { service.initialize(); }
}
