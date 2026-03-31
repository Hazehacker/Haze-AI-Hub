package top.hazenix.hazeaihub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 媒体文件实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_media")
@Schema(description = "媒体文件")
public class KbMedia {

    @Schema(description = "媒体ID[主键]")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属知识库ID")
    @NotNull(message = "所属知识库ID不能为空")
    private Long libraryId;

    @Schema(description = "原始文件名")
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @Schema(description = "MIME类型")
    @NotBlank(message = "MIME类型不能为空")
    private String mimeType;

    @Schema(description = "文件大小(字节)")
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    @Schema(description = "存储路径/OSS Key")
    @NotBlank(message = "存储路径不能为空")
    private String storagePath;

    @Schema(description = "内容哈希(SHA256)")
    @NotBlank(message = "SHA256不能为空")
    private String sha256;

    @Schema(description = "解析状态: PENDING/PARSING/PARSED/FAILED")
    @Builder.Default
    private String status = MediaStatus.PENDING.getCode();

    @Schema(description = "总分片数")
    @Builder.Default
    private Integer totalChunks = 0;

    @Schema(description = "已解析分片数")
    @Builder.Default
    private Integer parsedChunks = 0;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "QA生成状态: PENDING/GENERATING/GENERATED/FAILED")
    @Builder.Default
    private String qaStatus = QaStatus.PENDING.getCode();

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
