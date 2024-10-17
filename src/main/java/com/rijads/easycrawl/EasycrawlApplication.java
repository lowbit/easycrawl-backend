package com.rijads.easycrawl;

import com.rijads.easycrawl.config.RsaKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(RsaKeyProperties.class)
@SpringBootApplication
public class EasycrawlApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasycrawlApplication.class, args);
    }
}
