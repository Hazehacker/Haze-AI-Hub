package top.hazenix.hazeaihub.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: 模型信息实体类
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Model {

    @Schema(description = "模型id[主键]")
    private Long id;

    @Schema(description = "模型名称[唯一标识符]")
    private String name;

    @Schema(description = "模型描述")
    private String description;

    @Schema(description = "是否推荐")
    @TableField("is_recommended")
    private Boolean isRecommended;

    @Schema(description = "是否为Beta版本")
    @TableField("is_beta")
    private Boolean isBeta;

    @Schema(description = "排序字段")
    private Integer sort;

    @Schema(description = "是否启用", example = "true")
    private Boolean status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
