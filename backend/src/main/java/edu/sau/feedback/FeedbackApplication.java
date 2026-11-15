package edu.sau.feedback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/** Entry point. Tier 2 (application) of the three-tier architecture. */
@SpringBootApplication
@EnableAsync   // email and SMS are sent in the background so a slow mail server never slows a request
public class FeedbackApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeedbackApplication.class, args);
    }
}
