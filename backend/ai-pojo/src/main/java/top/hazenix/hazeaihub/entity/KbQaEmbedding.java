package top.hazenix.hazeaihub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * QA向量实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_qa_embedding")
@Schema(description = "QA向量")
public class KbQaEmbedding {

    @Schema(description = "向量ID[主键]")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联的QA对ID")
    @NotNull(message = "关联QA对ID不能为空")
    private Long qaPairId;

    @Schema(description = "QA问题向量(1024维)")
    private float[] embedding;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
