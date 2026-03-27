package top.hazenix.hazeaihub.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
import top.hazenix.hazeaihub.service.IAstraSearchService;
import top.hazenix.hazeaihub.service.IChatSessionService;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final ChatClient astraClient;

    // 混合检索参数
    private static final int BM25_TOP_K = 50;
    private static final int VECTOR_TOP_K = 50;
    private static final int RERANK_TOP_K = 10;

    @Override
    public List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK) {
        log.debug("混合检索: libraryId={}, query={}", libraryId, query);

        // 1. 检查知识库是否有数据
        long chunkCount = chunkMapper.countByLibraryId(libraryId);
        if (chunkCount == 0) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_EMPTY);
        }

        // 2. TODO: BM25 关键词检索
        // List<ChunkResponse> bm25Results = bm25Search(query, BM25_TOP_K);

        // 3. TODO: 向量相似度检索
        // List<ChunkResponse> vectorResults = vectorSearch(query, VECTOR_TOP_K);

        // 4. TODO: RRF 合并去重
        // List<ChunkResponse> mergedResults = rrfMerge(bm25Results, vectorResults);

        // 5. 后过滤（去重、长度过滤）

        // 6. 返回合并后的结果
        // 暂时返回空列表，等后续实现向量检索后完善
        return new ArrayList<>();
    }

    @Override
    public List<ChunkResponse> rerank(Long libraryId, String query, List<ChunkResponse> chunks, int topK) {
        log.debug("ReRank重排序: libraryId={}, chunks={}", libraryId, chunks.size());

        // TODO: 调用 DashScope bge-reranker-v2-m3 进行重排序
        // 目前暂时直接返回原始顺序

        if (chunks.size() <= topK) {
            return chunks;
        }
        return chunks.subList(0, topK);
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
            String fileName = media != null ? media.getFileName() : null;
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
