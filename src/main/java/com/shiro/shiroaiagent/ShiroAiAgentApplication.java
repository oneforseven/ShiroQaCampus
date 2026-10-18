package com.shiro.shiroaiagent;

import com.shiro.shiroaiagent.rag.PgVectorVectorStoreConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShiroAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiroAiAgentApplication.class, args);
    }

}
