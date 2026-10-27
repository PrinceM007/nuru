package com.nuru;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NuruApplication {

  public static void main(String[] args) {
    SpringApplication.run(NuruApplication.class, args);
  }
}
