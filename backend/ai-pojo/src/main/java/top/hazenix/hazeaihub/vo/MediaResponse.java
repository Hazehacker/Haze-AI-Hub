package top.hazenix.hazeaihub.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 媒体文件响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "媒体文件响应")
public class MediaResponse {

    @Schema(description = "媒体ID")
    private Long id;

    @Schema(description = "所属知识库ID")
    private Long libraryId;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "MIME类型")
    private String mimeType;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Schema(description = "存储路径/OSS Key")
    private String storagePath;

    @Schema(description = "内容哈希(SHA256)")
    private String sha256;

    @Schema(description = "解析状态: PENDING/PARSING/PARSED/FAILED")
    private String status;

    @Schema(description = "总分片数")
    private Integer totalChunks;

    @Schema(description = "已解析分片数")
    private Integer parsedChunks;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 获取解析进度百分比
     */
    public Integer getProgressPercent() {
        if (totalChunks == null || totalChunks == 0) {
            return 0;
        }
        return (int) ((parsedChunks * 100) / totalChunks);
    }
}
