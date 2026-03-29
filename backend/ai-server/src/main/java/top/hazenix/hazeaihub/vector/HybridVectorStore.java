package top.hazenix.hazeaihub.vector;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import top.hazenix.hazeaihub.entity.KbChunk;
import top.hazenix.hazeaihub.mapper.KbChunkMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量存储封装
 * <p>
 * 封装 KbChunkMapper，提供向量存储能力（不实现 VectorStore 接口以兼容 Spring AI 1.1.2）
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class HybridVectorStore {

    private final KbChunkMapper chunkMapper;
    private final DashScopeEmbeddingModel embeddingModel;

    private static final int BATCH_SIZE = 100;

    /**
     * 添加文档列表
     * @return 插入的 KbChunk 列表
     */
    public List<KbChunk> add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 提取文本内容
        List<String> contents = documents.stream()
                .map(Document::getText)
                .collect(Collectors.toList());

        // 2. 批量生成 embedding
        List<float[]> embeddings;
        try {
            embeddings = embeddingModel.embed(contents);
        } catch (Exception e) {
            log.error("生成 embedding 失败", e);
            return new ArrayList<>();
        }

        // 3. 转换为 KbChunk
        List<KbChunk> chunks = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            KbChunk chunk = toKbChunk(doc, embeddings.get(i));
            chunks.add(chunk);
        }

        // 4. 批量插入
        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, chunks.size());
            chunkMapper.batchInsert(chunks.subList(i, end));
        }

        log.info("VectorStore 添加文档: {} 条", documents.size());
        return chunks;
    }

    /**
     * 删除文档列表
     */
    public void delete(List<String> ids) {
        for (String id : ids) {
            try {
                Long chunkId = extractChunkId(id);
                if (chunkId != null) {
                    chunkMapper.deleteById(chunkId);
                }
            } catch (Exception e) {
                log.warn("删除文档失败: id={}", id, e);
            }
        }
        log.info("VectorStore 删除文档: {} 条", ids.size());
    }

    /**
     * 获取原生客户端
     */
    public KbChunkMapper getNativeClient() {
        return chunkMapper;
    }

    /**
     * Document 转换为 KbChunk
     */
    private KbChunk toKbChunk(Document doc, float[] embedding) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>(doc.getMetadata());
        Long libraryId = extractLong(metadata.remove("libraryId"));
        Long mediaId = extractLong(metadata.remove("mediaId"));
        Integer chunkIndex = extractInt(metadata.remove("chunkIndex"));

        return KbChunk.builder()
                .libraryId(libraryId)
                .mediaId(mediaId)
                .content(doc.getText())
                .embedding(embedding)
                .chunkIndex(chunkIndex)
                .metadata(metadata)
                .build();
    }

    /**
     * 从 ID 中提取 KbChunk ID
     */
    private Long extractChunkId(String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("kbchunk_")) {
            return Long.parseLong(id.substring("kbchunk_".length()));
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long extractLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}