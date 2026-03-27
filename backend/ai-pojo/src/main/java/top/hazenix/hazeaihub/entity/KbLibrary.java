package top.hazenix.hazeaihub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_library")
@Schema(description = "知识库")
public class KbLibrary {

    @Schema(description = "知识库ID[主键]")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "知识库名称")
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 32, message = "知识库名称最大32字符")
    private String name;

    @Schema(description = "知识库描述")
    @Size(max = 255, message = "知识库描述最大255字符")
    private String description;

    @Schema(description = "知识库类型: personal/team")
    @NotBlank(message = "知识库类型不能为空")
    private String type;

    @Schema(description = "所属用户ID")
    @NotNull(message = "所属用户ID不能为空")
    private Long ownerId;

    @Schema(description = "是否置顶")
    @Builder.Default
    private Boolean isTop = false;

    @Schema(description = "封面图片地址")
    private String coverImage;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
