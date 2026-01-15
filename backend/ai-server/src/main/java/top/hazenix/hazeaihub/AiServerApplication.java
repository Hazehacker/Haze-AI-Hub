package top.hazenix.hazeaihub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiServerApplication {

    public static void main(String[] args) {
        // 优先使用 IPv4，避免 IPv6 连接问题
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        
        SpringApplication.run(AiServerApplication.class, args);
    }

}
