package top.hazenix.hazeaihub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "astra")
public class AstraProperties {

    private QueryRewrite queryRewrite = new QueryRewrite();
    private Search search = new Search();
    private Rerank rerank = new Rerank();

    @Data
    public static class QueryRewrite {
        private boolean enabled = true;
        private String model = "qwen-turbo";
        private int maxTokens = 128;
        private double temperature = 0.3;
    }

    @Data
    public static class Search {
        private Fusion fusion = new Fusion();
        private TopK topK = new TopK();

        @Data
        public static class Fusion {
            private double alpha = 0.5;
            private double threshold = 0.3;
        }

        @Data
        public static class TopK {
            private int bm25 = 50;
            private int vector = 50;
        }
    }

    @Data
    public static class Rerank {
        private boolean enabled = true;
        private String model = "bge-reranker-v2-m3";
        private int topK = 10;
    }
}