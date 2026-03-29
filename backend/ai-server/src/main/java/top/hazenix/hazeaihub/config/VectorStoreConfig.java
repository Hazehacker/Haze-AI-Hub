package top.hazenix.hazeaihub.config;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.hazenix.hazeaihub.mapper.KbChunkMapper;
import top.hazenix.hazeaihub.vector.HybridVectorStore;

/**
 * VectorStore 配置
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public HybridVectorStore hybridVectorStore(
            KbChunkMapper chunkMapper,
            DashScopeEmbeddingModel embeddingModel) {
        return new HybridVectorStore(chunkMapper, embeddingModel);
    }
}