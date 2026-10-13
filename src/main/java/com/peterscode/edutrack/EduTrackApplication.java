package com.peterscode.edutrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class EduTrackApplication {

    static void main(String[] args) {
        SpringApplication.run(EduTrackApplication.class, args);
    }

}
