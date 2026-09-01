package com.easylive.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
})
@ComponentScan(basePackages = {
        "com.easylive.agent",
        "com.easylive.redis",
        "com.easylive.config",
        "com.easylive.auth",
        "com.easylive.utils"
})
@EnableDiscoveryClient
@EnableFeignClients
public class EasyLiveAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyLiveAgentApplication.class, args);
    }
}
