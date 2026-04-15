package top.hazenix.hazeaihub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
import top.hazenix.hazeaihub.handler.JsonTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库分片实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_chunk")
@Schema(description = "知识库分片")
public class KbChunk {

    @Schema(description = "分片ID[主键]")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属知识库ID")
    @NotNull(message = "所属知识库ID不能为空")
    private Long libraryId;

    @Schema(description = "关联媒体文件ID")
    @NotNull(message = "关联媒体文件ID不能为空")
    private Long mediaId;

    @Schema(description = "原始文本内容")
    @NotBlank(message = "内容不能为空")
    private String content;

    @Schema(description = "向量嵌入(1024维)")
    private float[] embedding;

    @Schema(description = "分片序号")
    @NotNull(message = "分片序号不能为空")
    private Integer chunkIndex;

    @Schema(description = "扩展元信息")
    @TableField(typeHandler = JsonTypeHandler.class)
    private Map<String, Object> metadata;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "中文分词 tsvector（DB自动维护）")
    @TableField(exist = false)
    private String searchVector;
}
