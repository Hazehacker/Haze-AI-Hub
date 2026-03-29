package top.hazenix.hazeaihub.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.entity.KbChunk;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.mapper.KbChunkMapper;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.utils.AliOssUtil;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Word (.docx) 文件解析器
 */
@Slf4j
@Component
public class WordFileParser implements FileParser {

    private static final String FILE_TYPE = "DOCX";

    private final KbMediaMapper mediaMapper;
    private final KbChunkMapper chunkMapper;
    private final ChunkingService chunkingService;
    private final AliOssUtil aliOssUtil;

    public WordFileParser(KbMediaMapper mediaMapper, KbChunkMapper chunkMapper,
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
        log.info("开始解析Word: mediaId={}, ossKey={}", message.getMediaId(), message.getOssKey());

        List<ChunkResponse> allChunks = new ArrayList<>();

        try {
            // 1. 获取媒体信息
            KbMedia media = mediaMapper.selectById(message.getMediaId());
            if (media == null) {
                throw new RuntimeException("媒体文件不存在: " + message.getMediaId());
            }

            // 2. 从 OSS 下载文件
            byte[] fileBytes = aliOssUtil.download(message.getOssKey());

            // 3. 解析 Word 文档
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
                List<XWPFParagraph> paragraphs = document.getParagraphs();
                log.info("Word文档段落数: {}, mediaId={}", paragraphs.size(), message.getMediaId());

                StringBuilder fullText = new StringBuilder();
                int paragraphIndex = 0;

                for (XWPFParagraph paragraph : paragraphs) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        fullText.append(text).append("\n");
                    }
                    paragraphIndex++;
                }

                // 智能分块
                List<String> textChunks = chunkingService.chunk(fullText.toString());
                for (String content : textChunks) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("fileName", media.getFileName());
                    metadata.put("paragraphCount", paragraphs.size());

                    // 保存到数据库
                    KbChunk chunk = KbChunk.builder()
                            .libraryId(message.getLibraryId())
                            .mediaId(message.getMediaId())
                            .content(content)
                            .chunkIndex(allChunks.size())
                            .metadata(metadata)
                            .build();
                    chunkMapper.insert(chunk);

                    // 添加到响应列表
                    allChunks.add(toChunkResponse(chunk, media));
                }
            }

            log.info("Word解析完成: mediaId={}, chunks={}", message.getMediaId(), allChunks.size());

        } catch (Exception e) {
            log.error("Word解析失败: mediaId={}", message.getMediaId(), e);
            throw new RuntimeException("Word解析失败: " + e.getMessage(), e);
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
