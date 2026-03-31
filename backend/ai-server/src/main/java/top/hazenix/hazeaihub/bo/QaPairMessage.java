package top.hazenix.hazeaihub.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QA对生成消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaPairMessage {

    /**
     * 媒体文件ID
     */
    private Long mediaId;

    /**
     * 知识库ID
     */
    private Long libraryId;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间戳
     */
    private Long createdAt;

    public void incrementRetry() {
        this.retryCount = (this.retryCount != null ? this.retryCount : 0) + 1;
    }
}
