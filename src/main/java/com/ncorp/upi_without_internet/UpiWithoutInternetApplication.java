package com.ncorp.upi_without_internet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UpiWithoutInternetApplication {

	public static void main(String[] args) {
		SpringApplication.run(UpiWithoutInternetApplication.class, args);
	}

}
