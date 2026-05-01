package com.dong.dongrag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.dong.dongrag.mapper")
@EnableScheduling
public class DongRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(DongRagApplication.class, args);
    }

}
