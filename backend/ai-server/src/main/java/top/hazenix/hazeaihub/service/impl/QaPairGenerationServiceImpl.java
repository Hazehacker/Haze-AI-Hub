package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.constant.PromptConstant;
import top.hazenix.hazeaihub.entity.*;
import top.hazenix.hazeaihub.mapper.KbChunkMapper;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.mapper.QaEmbeddingMapper;
import top.hazenix.hazeaihub.mapper.QaPairMapper;
import top.hazenix.hazeaihub.service.IQaPairGenerationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QA对生成服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaPairGenerationServiceImpl implements IQaPairGenerationService {

    private final KbChunkMapper chunkMapper;
    private final KbMediaMapper mediaMapper;
    private final QaPairMapper qaPairMapper;
    private final QaEmbeddingMapper qaEmbeddingMapper;
    private final DashScopeChatModel chatModel;
    private final DashScopeEmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final int BATCH_SIZE = 10;

    @Override
    public void generateQaPairs(Long mediaId) {
        log.info("开始为媒体文件生成QA对: mediaId={}", mediaId);

        // 1. 更新媒体状态为 GENERATING
        KbMedia media = mediaMapper.selectById(mediaId);
        if (media == null) {
            log.warn("媒体文件不存在: mediaId={}", mediaId);
            return;
        }

        media.setQaStatus(QaStatus.GENERATING.getCode());
        mediaMapper.updateById(media);

        try {
            // 2. 获取该媒体的所有Chunk
            List<KbChunk> chunks = chunkMapper.listByMediaId(mediaId);
            if (chunks.isEmpty()) {
                log.warn("媒体文件没有Chunk: mediaId={}", mediaId);
                media.setQaStatus(QaStatus.FAILED.getCode());
                media.setErrorMessage("没有可用的Chunk");
                mediaMapper.updateById(media);
                return;
            }

            log.info("媒体文件包含 {} 个Chunk: mediaId={}", chunks.size(), mediaId);

            // 3. 逐个Chunk生成QA对
            int successCount = 0;
            int failCount = 0;

            for (KbChunk chunk : chunks) {
                try {
                    generateQaPairForChunkInternal(chunk);
                    successCount++;
                } catch (Exception e) {
                    log.error("生成Chunk的QA对失败: chunkId={}", chunk.getId(), e);
                    failCount++;
                }

                // 批量处理时适当休息，避免 API 限流
                if (successCount % BATCH_SIZE == 0) {
                    Thread.sleep(100);
                }
            }

            // 4. 更新媒体状态
            if (failCount == 0) {
                media.setQaStatus(QaStatus.GENERATED.getCode());
                log.info("QA对生成完成: mediaId={}, success={}", mediaId, successCount);
            } else if (successCount > 0) {
                media.setQaStatus(QaStatus.GENERATED.getCode());
                log.warn("QA对生成部分失败: mediaId={}, success={}, fail={}", mediaId, successCount, failCount);
            } else {
                media.setQaStatus(QaStatus.FAILED.getCode());
                media.setErrorMessage("所有Chunk生成失败");
                log.error("QA对生成全部失败: mediaId={}", mediaId);
            }
            mediaMapper.updateById(media);

        } catch (Exception e) {
            log.error("生成QA对异常: mediaId={}", mediaId, e);
            media.setQaStatus(QaStatus.FAILED.getCode());
            media.setErrorMessage("生成异常: " + e.getMessage());
            mediaMapper.updateById(media);
        }
    }

    @Override
    public void generateQaPairForChunk(Long chunkId) {
        KbChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            log.warn("Chunk不存在: chunkId={}", chunkId);
            return;
        }

        try {
            generateQaPairForChunkInternal(chunk);
        } catch (Exception e) {
            log.error("生成Chunk的QA对失败: chunkId={}", chunkId, e);
            throw new RuntimeException("生成QA对失败", e);
        }
    }

    /**
     * 内部方法：为单个Chunk生成QA对
     */
    private void generateQaPairForChunkInternal(KbChunk chunk) throws Exception {
        // 1. 调用 LLM 生成 QA 对
        List<QaPairData> qaPairs = callLlmForQaPairs(chunk.getContent());

        if (qaPairs.isEmpty()) {
            log.warn("LLM 未生成任何 QA 对: chunkId={}", chunk.getId());
            return;
        }

        // 2. 转换为 KbQaPair 实体
        List<KbQaPair> qaPairEntities = new ArrayList<>();
        for (QaPairData qaData : qaPairs) {
            KbQaPair qaPair = KbQaPair.builder()
                    .chunkId(chunk.getId())
                    .question(qaData.question)
                    .answer(qaData.answer)
                    .libraryId(chunk.getLibraryId())
                    .mediaId(chunk.getMediaId())
                    .build();
            qaPairEntities.add(qaPair);
        }

        // 3. 批量插入 QA 对
        qaPairMapper.batchInsert(qaPairEntities);

        // 4. 批量生成 QA 向量
        List<String> questions = qaPairEntities.stream()
                .map(KbQaPair::getQuestion)
                .toList();

        List<float[]> embeddings;
        try {
            embeddings = embeddingModel.embed(questions);
        } catch (Exception e) {
            log.error("生成 QA 向量失败，删除已插入的 QA 对: chunkId={}", chunk.getId());
            // 嵌入生成失败时，删除已插入的 QA 对以保证原子性
            for (KbQaPair qaPair : qaPairEntities) {
                qaPairMapper.deleteById(qaPair.getId());
            }
            throw new RuntimeException("生成向量失败", e);
        }

        // 5. 转换为 KbQaEmbedding 实体并插入
        List<KbQaEmbedding> embeddingEntities = new ArrayList<>();
        for (int i = 0; i < qaPairEntities.size(); i++) {
            KbQaEmbedding embedding = KbQaEmbedding.builder()
                    .qaPairId(qaPairEntities.get(i).getId())
                    .embedding(embeddings.get(i))
                    .build();
            embeddingEntities.add(embedding);
        }

        try {
            qaEmbeddingMapper.batchInsert(embeddingEntities);
        } catch (Exception e) {
            log.error("插入 QA 嵌入失败，删除已插入的 QA 对: chunkId={}", chunk.getId());
            // 嵌入插入失败时，删除已插入的 QA 对以保证原子性
            for (KbQaPair qaPair : qaPairEntities) {
                qaPairMapper.deleteById(qaPair.getId());
            }
            throw new RuntimeException("插入向量失败", e);
        }

        log.debug("Chunk 生成 QA 对完成: chunkId={}, qaPairCount={}", chunk.getId(), qaPairs.size());
    }

    /**
     * 调用 LLM 生成 QA 对
     */
    private List<QaPairData> callLlmForQaPairs(String content) throws Exception {
        String prompt = String.format(PromptConstant.QA_GENERATION_PROMPT, content);

        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            try {
                Prompt chatPrompt = new Prompt(prompt);
                ChatResponse response = chatModel.call(chatPrompt);

                if (response != null && response.getResult() != null
                        && response.getResult().getOutput() != null) {

                    String responseText = response.getResult().getOutput().getText();
                    return parseQaPairsResponse(responseText);
                }
            } catch (Exception e) {
                log.warn("LLM 调用失败，重试中: retry={}, error={}", retry, e.getMessage());

                // 指数退避
                if (retry < MAX_RETRIES - 1) {
                    Thread.sleep((long) Math.pow(2, retry) * 1000);
                }
            }
        }

        throw new RuntimeException("LLM 调用失败，已达到最大重试次数");
    }

    /**
     * 解析 LLM 响应，提取 QA 对
     */
    private List<QaPairData> parseQaPairsResponse(String responseText) throws Exception {
        // 尝试提取 JSON 数组
        String jsonStr = extractJsonArray(responseText);

        if (jsonStr == null) {
            log.warn("无法从响应中提取 JSON: {}", responseText);
            return new ArrayList<>();
        }

        try {
            List<Map<String, String>> rawPairs = objectMapper.readValue(jsonStr,
                    new TypeReference<List<Map<String, String>>>() {});

            List<QaPairData> qaPairs = new ArrayList<>();
            for (Map<String, String> pair : rawPairs) {
                String question = pair.get("question");
                String answer = pair.get("answer");

                if (question != null && answer != null && !question.isBlank() && !answer.isBlank()) {
                    qaPairs.add(new QaPairData(question.trim(), answer.trim()));
                }
            }

            return qaPairs;
        } catch (Exception e) {
            log.warn("解析 QA 对 JSON 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从响应文本中提取 JSON 数组
     */
    private String extractJsonArray(String text) {
        // 尝试找到 JSON 数组的开始和结束
        int startIndex = text.indexOf('[');
        int endIndex = text.lastIndexOf(']');

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return text.substring(startIndex, endIndex + 1);
        }

        return null;
    }

    /**
     * QA 对数据类
     */
    private static class QaPairData {
        String question;
        String answer;

        QaPairData(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }
}
