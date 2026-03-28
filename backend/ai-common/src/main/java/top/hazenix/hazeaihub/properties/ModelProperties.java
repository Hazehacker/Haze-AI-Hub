package top.hazenix.hazeaihub.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.ai.dashscope")
public class ModelProperties {
    private String apiKey;
}
