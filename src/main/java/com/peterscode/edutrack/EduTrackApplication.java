package com.peterscode.edutrack;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import com.peterscode.edutrack.config.AzureKeyVaultInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class EduTrackApplication {

    public static void main(String[] args) {
        if (System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING") != null) {
            ApplicationInsights.attach();
        }

        SpringApplication application = new SpringApplication(EduTrackApplication.class);
        application.addInitializers(new AzureKeyVaultInitializer());
        application.run(args);
    }

}
