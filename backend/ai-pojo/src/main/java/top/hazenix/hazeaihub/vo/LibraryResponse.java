package top.hazenix.hazeaihub.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库响应")
public class LibraryResponse {

    @Schema(description = "知识库ID")
    private Long id;

    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "知识库描述")
    private String description;

    @Schema(description = "知识库类型: personal/team")
    private String type;

    @Schema(description = "所属用户ID")
    private Long ownerId;

    @Schema(description = "是否置顶")
    private Boolean isTop;

    @Schema(description = "封面图片地址")
    private String coverImage;

    @Schema(description = "媒体文件数量")
    private Long mediaCount;

    @Schema(description = "分片数量")
    private Long chunkCount;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
