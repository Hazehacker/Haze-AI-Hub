package top.hazenix.hazeaihub.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.entity.KbChunk;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.mapper.KbChunkMapper;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDF 文件解析器
 */
@Slf4j
@Component
public class PdfFileParser implements FileParser {

    private static final String FILE_TYPE = "PDF";

    private final KbMediaMapper mediaMapper;
    private final KbChunkMapper chunkMapper;
    private final ChunkingService chunkingService;

    public PdfFileParser(KbMediaMapper mediaMapper, KbChunkMapper chunkMapper, ChunkingService chunkingService) {
        this.mediaMapper = mediaMapper;
        this.chunkMapper = chunkMapper;
        this.chunkingService = chunkingService;
    }

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List<ChunkResponse> parse(ParseMessage message) {
        log.info("开始解析PDF: mediaId={}, ossKey={}", message.getMediaId(), message.getOssKey());

        List<ChunkResponse> allChunks = new ArrayList<>();

        try {
            // 1. 获取媒体信息
            KbMedia media = mediaMapper.selectById(message.getMediaId());
            if (media == null) {
                throw new RuntimeException("媒体文件不存在: " + message.getMediaId());
            }

            // 2. TODO: 从 OSS 下载文件到本地/内存
            // InputStream inputStream = ossUtil.getInputStream(message.getOssKey());
            // 暂时使用占位符

            // 3. 解析 PDF
            // try (PDDocument document = PDDocument.load(inputStream)) {
            //     PDFTextStripper stripper = new PDFTextStripper();
            //     stripper.setSortByPosition(true);
            //
            //     int totalPages = document.getNumberOfPages();
            //     for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
            //         stripper.setStartPage(pageNum);
            //         stripper.setEndPage(pageNum);
            //         String pageText = stripper.getText(document);
            //
            //         // 智能分块
            //         List<String> chunks = chunkingService.chunk(pageText);
            //         for (int i = 0; i < chunks.size(); i++) {
            //             String content = chunks.get(i);
            //             Map<String, Object> metadata = new HashMap<>();
            //             metadata.put("page", pageNum);
            //             metadata.put("fileName", media.getFileName());
            //
            //             // 保存到数据库
            //             KbChunk chunk = KbChunk.builder()
            //                     .libraryId(message.getLibraryId())
            //                     .mediaId(message.getMediaId())
            //                     .content(content)
            //                     .chunkIndex(allChunks.size())
            //                     .metadata(metadata)
            //                     .build();
            //             chunkMapper.insert(chunk);
            //
            //             // 添加到响应列表
            //             allChunks.add(toChunkResponse(chunk, media));
            //         }
            //     }
            // }

            // TODO: 暂时返回空列表，等 OSS 集成后完善
            log.info("PDF解析完成(待实现OSS集成): mediaId={}, chunks={}", message.getMediaId(), allChunks.size());

        } catch (Exception e) {
            log.error("PDF解析失败: mediaId={}", message.getMediaId(), e);
            throw new RuntimeException("PDF解析失败: " + e.getMessage(), e);
        }

        return allChunks;
    }

    private ChunkResponse toChunkResponse(KbChunk chunk, KbMedia media) {
        Map<String, Object> metadata = chunk.getMetadata();
        String source = null;
        if (metadata != null) {
            Object page = metadata.get("page");
            Object fileName = metadata.get("fileName");
            if (fileName != null && page != null) {
                source = fileName + "-第" + page + "页";
            } else if (fileName != null) {
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
