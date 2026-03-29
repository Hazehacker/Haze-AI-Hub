package top.hazenix.hazeaihub.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.utils.AliOssUtil;
import top.hazenix.hazeaihub.vector.HybridVectorStore;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown (.md) 文件解析器
 */
@Slf4j
@Component
public class MarkdownFileParser implements FileParser {

    private static final String FILE_TYPE = "MD";

    private final KbMediaMapper mediaMapper;
    private final HybridVectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final AliOssUtil aliOssUtil;

    public MarkdownFileParser(KbMediaMapper mediaMapper,
                              HybridVectorStore vectorStore,
                              ChunkingService chunkingService,
                              AliOssUtil aliOssUtil) {
        this.mediaMapper = mediaMapper;
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
        this.aliOssUtil = aliOssUtil;
    }

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List<ChunkResponse> parse(ParseMessage message) {
        log.info("开始解析Markdown: mediaId={}, ossKey={}", message.getMediaId(), message.getOssKey());

        List<Document> documents = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        try {
            // 1. 获取媒体信息
            KbMedia media = mediaMapper.selectById(message.getMediaId());
            if (media == null) {
                throw new RuntimeException("媒体文件不存在: " + message.getMediaId());
            }

            // 2. 从 OSS 下载文件
            byte[] fileBytes = aliOssUtil.download(message.getOssKey());

            // 3. 解析 Markdown（纯文本读取）
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            String[] lines = content.split("\n");

            log.info("Markdown文档行数: {}, mediaId={}", lines.length, message.getMediaId());

            // 智能分块
            List<String> textChunks = chunkingService.chunk(content);
            int chunkIndex = 0;
            for (String chunkContent : textChunks) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("fileName", media.getFileName());
                metadata.put("lineCount", lines.length);
                metadata.put("libraryId", message.getLibraryId());
                metadata.put("mediaId", message.getMediaId());
                metadata.put("chunkIndex", chunkIndex++);

                Document doc = Document.builder()
                        .text(chunkContent)
                        .metadata(metadata)
                        .build();
                documents.add(doc);
                sources.add(media.getFileName());
            }

            // 4. 使用 VectorStore 批量入库（自动生成 embedding）
            if (!documents.isEmpty()) {
                vectorStore.add(documents);
            }

            // 5. 转换为响应列表
            List<ChunkResponse> allChunks = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                allChunks.add(ChunkResponse.builder()
                        .content(doc.getText())
                        .metadata(metadata)
                        .source(sources.get(i))
                        .build());
            }

            log.info("Markdown解析完成: mediaId={}, chunks={}", message.getMediaId(), allChunks.size());
            return allChunks;

        } catch (Exception e) {
            log.error("Markdown解析失败: mediaId={}", message.getMediaId(), e);
            throw new RuntimeException("Markdown解析失败: " + e.getMessage(), e);
        }
    }
}