package com.example.poker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PokerGameApplication {
    public static void main(String[] args) {
        SpringApplication.run(PokerGameApplication.class, args);
    }
} 