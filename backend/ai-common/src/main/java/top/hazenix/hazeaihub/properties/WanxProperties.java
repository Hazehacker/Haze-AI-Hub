package top.hazenix.hazeaihub.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.wanx")
public class WanxProperties {
    private String apiKey;
    private String model = "wanx-v1";
    private String endpoint = "https://dashscope.aliyuncs.com/api/v1";
    private int timeout = 60000;
}