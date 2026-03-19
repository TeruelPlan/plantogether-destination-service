package com.plantogether.destination;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PlantogetherDestinationApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlantogetherDestinationApplication.class, args);
    }
}
