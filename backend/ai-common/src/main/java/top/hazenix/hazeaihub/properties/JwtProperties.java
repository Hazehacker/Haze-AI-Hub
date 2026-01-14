package top.hazenix.hazeaihub.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.jwt")
@Data
public class JwtProperties {

    /**
     * 管理端人员生成jwt令牌相关配置(未启用)
     */
//    private String adminSecretKey;
//    private long adminTtl;
//    private String adminTokenName;

    /**
     * 用户端用户生成jwt令牌相关配置
     */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;

}
