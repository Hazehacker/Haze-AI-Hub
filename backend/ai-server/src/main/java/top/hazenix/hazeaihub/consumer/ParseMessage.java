package top.hazenix.hazeaihub.consumer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 解析任务消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 媒体文件ID
     */
    private Long mediaId;

    /**
     * 所属知识库ID
     */
    private Long libraryId;

    /**
     * 文件类型（PDF/WORD/IMAGE/AUDIO/XMIND）
     */
    private String fileType;

    /**
     * OSS 文件路径
     */
    private String ossKey;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 入队时间戳（毫秒）
     */
    private Long createdAt;

    /**
     * 创建消息
     */
    public static ParseMessage of(Long mediaId, Long libraryId, String fileType, String ossKey) {
        return ParseMessage.builder()
                .mediaId(mediaId)
                .libraryId(libraryId)
                .fileType(fileType)
                .ossKey(ossKey)
                .retryCount(0)
                .createdAt(System.currentTimeMillis())
                .build();
    }

    /**
     * 增加重试次数
     */
    public ParseMessage incrementRetry() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        return this;
    }
}
