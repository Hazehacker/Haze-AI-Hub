package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.dto.AstraChatRequest;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.entity.KbChunk;
import top.hazenix.hazeaihub.entity.KbLibrary;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.enums.ErrorCode;
import top.hazenix.hazeaihub.exception.BusinessException;
import top.hazenix.hazeaihub.mapper.KbChunkMapper;
import top.hazenix.hazeaihub.mapper.KbLibraryMapper;
import top.hazenix.hazeaihub.properties.AstraProperties;
import top.hazenix.hazeaihub.properties.ModelProperties;
import top.hazenix.hazeaihub.service.IAstraSearchService;
import top.hazenix.hazeaihub.service.IChatSessionService;
import top.hazenix.hazeaihub.service.IQueryRewriteService;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankParam;

/**
 * Astra RAG 检索服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AstraSearchServiceImpl implements IAstraSearchService {

    private final KbLibraryMapper libraryMapper;
    private final KbChunkMapper chunkMapper;
    private final IChatSessionService chatSessionService;
    private final DashScopeEmbeddingModel embeddingModel;
    private final ChatClient astraClient;
    private final IQueryRewriteService queryRewriteService;
    private final AstraProperties astraProperties;
    private final ModelProperties modelProperties;

    // 混合检索参数
    private static final int RERANK_TOP_K = 10;

    @Override
    public List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK) {
        log.debug("混合检索: libraryId={}, query={}", libraryId, query);

        // 1. 检查知识库是否有数据
        long chunkCount = chunkMapper.countByLibraryId(libraryId);
        if (chunkCount == 0) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_EMPTY);
        }

        // 2. Query 重写（可选，失败时降级为原始 query）
        String rewrittenQuery;
        try {
            rewrittenQuery = queryRewriteService.rewrite(query);
        } catch (Exception e) {
            log.warn("Query 重写失败，使用原始 query: query={}", query, e);
            rewrittenQuery = query;
        }

        // 3. 生成查询向量
        float[] queryEmbedding = embeddingModel.embed(rewrittenQuery);

        // 4. 准备检索参数（全部从配置读取，禁止硬编码）
        String queryTerms = rewrittenQuery.toLowerCase().replaceAll("\\s+", " ");
        int bm25TopK = astraProperties.getSearch().getTopK().getBm25();
        int vectorTopK = astraProperties.getSearch().getTopK().getVector();
        int efSearch = astraProperties.getSearch().getEfSearch();
        int rrfK = astraProperties.getSearch().getRrfK();
        int rrfOutputTopK = astraProperties.getSearch().getRrfOutputTopK();

        // 5. 单条 SQL 完成双路召回 + RRF 合并（下推到 PostgreSQL）
        List<Map<String, Object>> resultMaps = chunkMapper.hybridSearchRrf(
                libraryId, queryEmbedding, queryTerms,
                vectorTopK, bm25TopK, efSearch, rrfK, rrfOutputTopK);

        // 6. 转换为 ChunkResponse
        return resultMaps.stream()
                .map(map -> {
                    KbChunk chunk = mapToChunk(map);
                    ChunkResponse response = toChunkResponse(chunk, null);
                    Object rrfScore = map.get("rrf_score");
                    if (rrfScore != null) {
                        response.setScore(((Number) rrfScore).floatValue());
                    }
                    Object vectorScore = map.get("vector_score");
                    if (vectorScore != null) {
                        response.setVectorScore(((Number) vectorScore).floatValue());
                    }
                    Object bm25Score = map.get("bm25_score");
                    if (bm25Score != null) {
                        response.setBm25Score(((Number) bm25Score).floatValue());
                    }
                    return response;
                })
                .collect(Collectors.toList());
    }

    /**
     * 将 Map 转换为 KbChunk
     */
    private KbChunk mapToChunk(Map<String, Object> map) {
        KbChunk chunk = new KbChunk();
        chunk.setId(((Number) map.get("id")).longValue());
        chunk.setLibraryId(((Number) map.get("library_id")).longValue());
        chunk.setMediaId(((Number) map.get("media_id")).longValue());
        chunk.setContent((String) map.get("content"));
        chunk.setChunkIndex(((Number) map.get("chunk_index")).intValue());
        // metadata 和 embedding 需要特殊处理，这里简化处理
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
        chunk.setMetadata(metadata);
        return chunk;
    }

    @Override
    public List<ChunkResponse> rerank(Long libraryId, String query, List<ChunkResponse> chunks, int topK) {
        // 如果禁用rerank或为空，直接返回
        if (!astraProperties.getRerank().isEnabled()) {
            log.debug("ReRank已禁用，返回原始顺序");
            return chunks.subList(0, Math.min(chunks.size(), topK));
        }

        if (chunks == null || chunks.isEmpty()) {
            return chunks;
        }

        log.debug("ReRank重排序: libraryId={}, query={}, chunks={}, topK={}",
                libraryId, query, chunks.size(), topK);

        try {
            // 调用 DashScope bge-reranker-v2-m3 API
            List<ChunkResponse> rerankedChunks = callDashscopeRerank(query, chunks);
            log.debug("ReRank完成，返回{}个结果", rerankedChunks.size());
            return rerankedChunks;

        } catch (Exception e) {
            log.error("ReRank重排序失败，退回原始顺序: libraryId={}", libraryId, e);
            return chunks.subList(0, Math.min(chunks.size(), topK));
        }
    }

    /**
     * 调用 qwen3-rerank API 进行文档重排序
     */
    private List<ChunkResponse> callDashscopeRerank(String query, List<ChunkResponse> chunks) {
        TextReRank rerank = new TextReRank();

        // 构建文档列表
        List<String> documents = chunks.stream()
            .map(chunk -> chunk.getContent() != null ? chunk.getContent() : "")
            .collect(Collectors.toList());

        // 构建参数
        TextReRankParam param = TextReRankParam.builder()
            .model(astraProperties.getRerank().getModel())
            .query(query)
            .documents(documents)
            .topN(astraProperties.getRerank().getTopK())
            .returnDocuments(true)
            .build();

        // 调用 SDK
        Object result = rerank.call(param);

        // 解析结果
        return parseRerankResult(result, chunks);
    }

    /**
     * 解析 DashScope SDK ReRank 结果
     */
    @SuppressWarnings("unchecked")
    private List<ChunkResponse> parseRerankResult(Object result, List<ChunkResponse> chunks) {
        try {
            // SDK 返回结果可能是 Map 结构
            Map<String, Object> resultMap = (Map<String, Object>) result;

            // 提取 results 数组
            List<Map<String, Object>> results = (List<Map<String, Object>>) resultMap.get("output");
            if (results == null) {
                log.warn("ReRank SDK 结果中无 output 字段");
                return chunks;
            }

            // 构建 index -> score 映射
            Map<Integer, Float> scoreMap = new HashMap<>();
            for (Map<String, Object> item : results) {
                int index = ((Number) item.get("index")).intValue();
                double score = ((Number) item.get("relevance_score")).doubleValue();
                scoreMap.put(index, (float) score);
            }

            // 更新 chunks 分数并排序
            for (int i = 0; i < chunks.size(); i++) {
                ChunkResponse chunk = chunks.get(i);
                if (scoreMap.containsKey(i)) {
                    chunk.setScore(scoreMap.get(i));
                }
            }

            // 按分数降序排序
            return chunks.stream()
                    .sorted(Comparator.comparing(c -> c.getScore() != null ? c.getScore() : 0f, Comparator.reverseOrder()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("解析 ReRank SDK 结果失败: {}", e.getMessage());
            return chunks;
        }
    }

    @Override
    public String buildPrompt(String query, List<ChunkResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "用户问题: " + query + "\n\n参考内容: 无相关文档";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkResponse chunk = chunks.get(i);
            String source = chunk.getSource() != null ? chunk.getSource() : "未知来源";
            context.append(String.format("[%s] %s\n\n", source, chunk.getContent()));
        }

        return String.format("""
                # 知识库问答

                ## 用户问题
                %s

                ## 参考内容
                %s

                ## 回答要求
                1. 引用时标注来源
                2. 不知道的内容明确说明
                3. 回答简洁有据
                """, query, context.toString());
    }

    @Override
    public Flux<String> chat(Long userId, AstraChatRequest request) {
        log.info("Astra知识问答: userId={}, libraryId={}, prompt={}",
                userId, request.getLibraryId(), request.getPrompt());

        // 1. 校验知识库存在
        KbLibrary library = libraryMapper.selectById(request.getLibraryId());
        if (library == null) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_NOT_FOUND);
        }

        // 2. 校验权限
        if (!library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问此知识库");
        }

        // 3. 获取或创建会话
        ChatSession session = getOrCreateSession(userId, request.getLibraryId(), request.getSessionId());

        // 4. 发送 session-created 事件
        String sessionCreatedEvent = String.format("event: session-created\ndata: {\"sessionId\":%d}\n\n", session.getId());

        // 5. 混合检索
        List<ChunkResponse> searchResults;
        try {
            searchResults = hybridSearch(request.getLibraryId(), request.getPrompt(), RERANK_TOP_K);
        } catch (BusinessException e) {
            if (ErrorCode.ASTRA_LIBRARY_EMPTY.getCode().equals(e.getCode())) {
                // 知识库为空，返回提示
                String emptyEvent = "event: answer\ndata: 抱歉，该知识库还没有上传任何文档，无法回答您的问题。\n\n";
                String completeEvent = "event: complete\ndata: {\"messageId\":null}\n\n";
                return Flux.just(sessionCreatedEvent + emptyEvent + completeEvent);
            }
            throw e;
        }

        // 6. ReRank 重排序
        List<ChunkResponse> rerankedResults = rerank(request.getLibraryId(), request.getPrompt(), searchResults, RERANK_TOP_K);

        // 7. 构建 Prompt
        String prompt = buildPrompt(request.getPrompt(), rerankedResults);

        // 8. 流式调用 LLM
        Flux<String> thinkingEvent = Flux.just("event: thinking\ndata: 正在检索相关文档...\n\n");
        Flux<String> rerankEvent = Flux.just("event: thinking\ndata: 找到" + rerankedResults.size() + "个相关片段，进行重排序...\n\n");

        Flux<String> answerFlux = astraClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(content -> "event: answer\ndata: " + content + "\n\n");

        // 9. 拼接完整响应
        return Flux.concat(
                Flux.just(sessionCreatedEvent),
                thinkingEvent,
                rerankEvent,
                answerFlux,
                Flux.just("event: complete\ndata: {\"messageId\":" + session.getId() + "}\n\n")
        );
    }

    @Override
    public ChatSession getOrCreateSession(Long userId, Long libraryId, Long sessionId) {
        if (sessionId != null) {
            // 验证会话存在且属于该用户
            ChatSession session = chatSessionService.getSessionById(sessionId);
            if (session != null && session.getUserId().equals(userId)) {
                return session;
            }
            throw new BusinessException(ErrorCode.ASTRA_SESSION_NOT_FOUND);
        }

        // 获取知识库名称作为会话标题前缀
        KbLibrary library = libraryMapper.selectById(libraryId);
        String title = (library != null ? library.getName() : "知识库") + " 问答";

        // 创建新会话
        return chatSessionService.createSession(userId, "astra", title);
    }

    /**
     * 将 KbChunk 转换为 ChunkResponse
     */
    private ChunkResponse toChunkResponse(KbChunk chunk, KbMedia media) {
        Map<String, Object> metadata = chunk.getMetadata();
        String source = null;
        if (metadata != null) {
            String fileName = null;
            if (media != null) {
                fileName = media.getFileName();
            } else {
                Object fileNameObj = metadata.get("fileName");
                if (fileNameObj != null) {
                    fileName = fileNameObj.toString();
                }
            }
            Object pageObj = metadata.get("page");
            if (fileName != null && pageObj != null) {
                source = fileName + "-第" + pageObj + "页";
            } else if (fileName != null) {
                source = fileName;
            }
        }

        return ChunkResponse.builder()
                .id(chunk.getId())
                .libraryId(chunk.getLibraryId())
                .mediaId(chunk.getMediaId())
                .content(chunk.getContent())
                .chunkIndex(chunk.getChunkIndex())
                .metadata(metadata)
                .source(source)
                .build();
    }
}
