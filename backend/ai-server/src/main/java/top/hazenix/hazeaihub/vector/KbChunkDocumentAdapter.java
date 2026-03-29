package top.hazenix.hazeaihub.vector;

import org.springframework.ai.document.Document;
import top.hazenix.hazeaihub.entity.KbChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * KbChunk 与 Document 适配器
 */
public class KbChunkDocumentAdapter {

    private static final String ID_PREFIX = "kbchunk_";

    /**
     * KbChunk 转换为 Document
     */
    public static Document toDocument(KbChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        if (chunk.getMetadata() != null) {
            metadata.putAll(chunk.getMetadata());
        }
        metadata.put("libraryId", chunk.getLibraryId());
        metadata.put("mediaId", chunk.getMediaId());
        metadata.put("chunkIndex", chunk.getChunkIndex());

        return Document.builder()
                .id(ID_PREFIX + chunk.getId())
                .text(chunk.getContent())
                .metadata(metadata)
                .build();
    }

    /**
     * Document 转换为 KbChunk（用于从 VectorStore 查询结果转换）
     */
    public static KbChunk toKbChunk(Document doc) {
        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
        Long libraryId = extractLong(metadata.remove("libraryId"));
        Long mediaId = extractLong(metadata.remove("mediaId"));
        Integer chunkIndex = extractInt(metadata.remove("chunkIndex"));

        Long chunkId = extractChunkId(doc.getId());

        return KbChunk.builder()
                .id(chunkId)
                .libraryId(libraryId)
                .mediaId(mediaId)
                .content(doc.getText())
                .chunkIndex(chunkIndex)
                .metadata(metadata)
                .build();
    }

    /**
     * 从 Document ID 中提取 KbChunk ID
     */
    public static Long extractChunkId(String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith(ID_PREFIX)) {
            return Long.parseLong(id.substring(ID_PREFIX.length()));
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long extractLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer extractInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}