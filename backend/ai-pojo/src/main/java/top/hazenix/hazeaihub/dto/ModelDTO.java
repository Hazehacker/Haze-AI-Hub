package top.hazenix.hazeaihub.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: 模型信息DTO
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDTO {

    @Schema(description = "模型id[主键]", example = "2")
    private Long id;

    @Schema(description = "模型名称[唯一标识符]", example = "Qwen3-Max-Thinking-Preview")
    @NotBlank(message = "模型名称不能为空")
    private String name;

    @Schema(description = "模型描述", example = "专为复杂推理与深度思考优化的模型")
    private String description;

    @Schema(description = "是否推荐")
    @TableField("is_recommended")
    private Boolean isRecommended;

    @Schema(description = "是否为Beta版本")
    @TableField("is_beta")
    private Boolean isBeta;

    @Schema(description = "是否启用", example = "true")
    private Boolean status;

    @Schema(description = "排序字段(降序排序)", example = "3")
    private Integer sort;
}
