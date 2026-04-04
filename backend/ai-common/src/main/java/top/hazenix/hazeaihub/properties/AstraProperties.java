package top.hazenix.hazeaihub.properties;

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
        private String model = "qwen-flash";
        private int maxTokens = 128;
        private double temperature = 0.3;
    }

    @Data
    public static class Search {
        private TopK topK = new TopK();
        /**
         * RRF 合并后的输出数量，取前 N 个送入 ReRank
         */
        private int rrfOutputTopK = 30;
        /**
         * RRF 平滑因子，值越大则高排名与低排名的分差越小
         * 通常取 60，详见 Reciprocal Rank Fusion 论文
         */
        private int rrfK = 60;
        /**
         * pgvector HNSW ef_search 参数：候选集大小，越大召回率越高但越慢
         * 配合 iterative_scan=relaxed_order 使用，建议范围 100~400
         */
        private int efSearch = 200;

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