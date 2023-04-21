package com.changdong.opc;

import com.changdong.opc.server.ExampleServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.changdong.opc"})
public class ServerApplication {

    public static ExampleServer server;

    public static void main(String[] args) throws Exception {
        server = new ExampleServer();
        server.startup().get();
        SpringApplication.run(ServerApplication.class, args);
    }

}
