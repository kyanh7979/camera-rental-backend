package com.camerarental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EntityScan("com.camerarental.entity")
@EnableJpaRepositories("com.camerarental.repository")
public class CameraRentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(CameraRentalApplication.class, args);
    }
}
