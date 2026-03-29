package top.hazenix.hazeaihub.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.utils.AliOssUtil;
import top.hazenix.hazeaihub.vector.HybridVectorStore;
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
    private final HybridVectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final AliOssUtil aliOssUtil;

    public WordFileParser(KbMediaMapper mediaMapper,
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
        log.info("开始解析Word: mediaId={}, ossKey={}", message.getMediaId(), message.getOssKey());

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

            // 3. 解析 Word 文档
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
                List<XWPFParagraph> paragraphs = document.getParagraphs();
                log.info("Word文档段落数: {}, mediaId={}", paragraphs.size(), message.getMediaId());

                StringBuilder fullText = new StringBuilder();

                for (XWPFParagraph paragraph : paragraphs) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        fullText.append(text).append("\n");
                    }
                }

                // 智能分块
                List<String> textChunks = chunkingService.chunk(fullText.toString());
                int chunkIndex = 0;
                for (String content : textChunks) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("fileName", media.getFileName());
                    metadata.put("paragraphCount", paragraphs.size());
                    metadata.put("libraryId", message.getLibraryId());
                    metadata.put("mediaId", message.getMediaId());
                    metadata.put("chunkIndex", chunkIndex++);

                    Document doc = Document.builder()
                            .text(content)
                            .metadata(metadata)
                            .build();
                    documents.add(doc);
                    sources.add(media.getFileName());
                }
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

            log.info("Word解析完成: mediaId={}, chunks={}", message.getMediaId(), allChunks.size());
            return allChunks;

        } catch (Exception e) {
            log.error("Word解析失败: mediaId={}", message.getMediaId(), e);
            throw new RuntimeException("Word解析失败: " + e.getMessage(), e);
        }
    }
}