package cn.openlims.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenLIMSApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenLIMSApplication.class, args);
    }
}
