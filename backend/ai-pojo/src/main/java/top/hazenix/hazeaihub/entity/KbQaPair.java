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
 * QA对实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_qa_pair")
@Schema(description = "QA对")
public class KbQaPair {

    @Schema(description = "QA对ID[主键]")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联的Chunk ID")
    @NotNull(message = "关联Chunk ID不能为空")
    private Long chunkId;

    @Schema(description = "生成的问题")
    @NotBlank(message = "问题不能为空")
    private String question;

    @Schema(description = "生成的答案")
    @NotBlank(message = "答案不能为空")
    private String answer;

    @Schema(description = "所属知识库ID")
    @NotNull(message = "所属知识库ID不能为空")
    private Long libraryId;

    @Schema(description = "所属媒体文件ID")
    @NotNull(message = "所属媒体文件ID不能为空")
    private Long mediaId;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
