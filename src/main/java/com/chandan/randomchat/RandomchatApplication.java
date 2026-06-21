package com.chandan.randomchat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class RandomchatApplication {

	public static void main(String[] args) {
		SpringApplication.run(RandomchatApplication.class, args);
		log.info("RandomChat backend started successfully");
	}
}
