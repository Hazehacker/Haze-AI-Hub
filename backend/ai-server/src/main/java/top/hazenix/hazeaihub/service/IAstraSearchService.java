package top.hazenix.hazeaihub.service;

import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.dto.AstraChatRequest;
import top.hazenix.hazeaihub.entity.ChatSession;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.util.List;

/**
 * Astra RAG 检索服务接口
 */
public interface IAstraSearchService {

    /**
     * 混合检索(BM25 + 向量)
     * @param libraryId 知识库ID
     * @param query 查询文本
     * @param topK 召回数量
     * @return 检索结果列表
     */
    List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK);

    /**
     * ReRank 重排序
     * @param libraryId 知识库ID
     * @param query 查询文本
     * @param chunks 待重排序的分片
     * @param topK 返回数量
     * @return 重排序后的分片列表
     */
    List<ChunkResponse> rerank(Long libraryId, String query, List<ChunkResponse> chunks, int topK);

    /**
     * 构建 RAG 问答 Prompt
     * @param query 用户问题
     * @param chunks 检索到的分片
     * @return 构建好的 Prompt
     */
    String buildPrompt(String query, List<ChunkResponse> chunks);

    /**
     * 执行知识问答(流式)
     * @param userId 用户ID
     * @param request 问答请求
     * @return SSE 事件流
     */
    Flux<String> chat(Long userId, AstraChatRequest request);

    /**
     * 创建或获取 Astra 会话
     * @param userId 用户ID
     * @param libraryId 知识库ID
     * @param sessionId 会话ID(null表示新建)
     * @return ChatSession
     */
    ChatSession getOrCreateSession(Long userId, Long libraryId, Long sessionId);
}
