package top.hazenix.hazeaihub.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wanx")
public class WanxProperties {
    private String apiKey;
    private String model = "wanxFLUX";
    private String endpoint = "https://dashscope.aliyuncs.com/api/v1/services/a2xlvm9xb7tx/image Generation";
    private int timeout = 60000;
}