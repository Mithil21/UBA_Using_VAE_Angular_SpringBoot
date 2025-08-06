package com.example.uba_research;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
public class UbaResearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(UbaResearchApplication.class, args);
	}

}
