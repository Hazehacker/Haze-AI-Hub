package top.hazenix.hazeaihub.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.entity.KbChunk;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.mapper.KbChunkMapper;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.utils.AliOssUtil;
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
    private final KbChunkMapper chunkMapper;
    private final ChunkingService chunkingService;
    private final AliOssUtil aliOssUtil;

    public MarkdownFileParser(KbMediaMapper mediaMapper, KbChunkMapper chunkMapper,
                              ChunkingService chunkingService, AliOssUtil aliOssUtil) {
        this.mediaMapper = mediaMapper;
        this.chunkMapper = chunkMapper;
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

        List<ChunkResponse> allChunks = new ArrayList<>();

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
            for (String chunkContent : textChunks) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("fileName", media.getFileName());
                metadata.put("lineCount", lines.length);

                // 保存到数据库
                KbChunk chunk = KbChunk.builder()
                        .libraryId(message.getLibraryId())
                        .mediaId(message.getMediaId())
                        .content(chunkContent)
                        .chunkIndex(allChunks.size())
                        .metadata(metadata)
                        .build();
                chunkMapper.insert(chunk);

                // 添加到响应列表
                allChunks.add(toChunkResponse(chunk, media));
            }

            log.info("Markdown解析完成: mediaId={}, chunks={}", message.getMediaId(), allChunks.size());

        } catch (Exception e) {
            log.error("Markdown解析失败: mediaId={}", message.getMediaId(), e);
            throw new RuntimeException("Markdown解析失败: " + e.getMessage(), e);
        }

        return allChunks;
    }

    private ChunkResponse toChunkResponse(KbChunk chunk, KbMedia media) {
        Map<String, Object> metadata = chunk.getMetadata();
        String source = null;
        if (metadata != null) {
            Object fileName = metadata.get("fileName");
            if (fileName != null) {
                source = fileName.toString();
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
