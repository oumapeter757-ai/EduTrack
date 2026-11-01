package com.peterscode.edutrack.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "mpesa")
public class MpesaConfig {

    private String tillNumber;
    private String shortCode;
    private String passkey;
    private String environment;
    private String callbackUrl;
}
