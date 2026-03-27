package top.hazenix.hazeaihub.parser;

import top.hazenix.hazeaihub.consumer.ParseMessage;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.util.List;

/**
 * 文件解析器接口
 */
public interface FileParser {

    /**
     * 获取支持的文件类型
     */
    String getFileType();

    /**
     * 解析文件并生成 Chunk
     * @param message 解析消息
     * @return 分片列表
     */
    List<ChunkResponse> parse(ParseMessage message);

    /**
     * 是否支持该文件类型
     */
    default boolean supports(String fileType) {
        return getFileType().equalsIgnoreCase(fileType);
    }
}
