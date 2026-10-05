package com.examplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExamPlatformApplication {
    public static void main(String[] args) {
        // Totes les dates es desen i es calculen en UTC (LocalDateTime sense zona). L'API les
        // envia amb "Z" (vegeu DatesConfig) i el navegador les mostra en l'hora local.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ExamPlatformApplication.class, args);
    }
}
