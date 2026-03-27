package top.hazenix.hazeaihub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Astra 知识库配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "astra")
public class AstraProperties {

    /**
     * 是否启用 Astra 功能
     */
    private boolean enabled = true;

    /**
     * 文件上传配置
     */
    private Upload upload = new Upload();

    /**
     * 向量化配置
     */
    private Embedding embedding = new Embedding();

    /**
     * 文件上传配置
     */
    @Data
    public static class Upload {
        /**
         * 最大文件大小 (MB)
         */
        private int maxFileSize = 100;

        /**
         * 允许的文件类型
         */
        private String[] allowedTypes = {
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "image/jpeg",
                "image/png",
                "image/gif",
                "image/webp",
                "audio/mpeg",
                "audio/wav",
                "application/x-xmind",
                "text/plain"
        };
    }

    /**
     * 向量化配置
     */
    @Data
    public static class Embedding {
        /**
         * Embedding 模型名称
         */
        private String model = "text-embedding-v3";

        /**
         * 向量维度
         */
        private int dimensions = 1024;

        /**
         * 最大输入 tokens
         */
        private int maxTokens = 8192;
    }
}
