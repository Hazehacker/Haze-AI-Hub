package top.hazenix.hazeaihub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建知识库请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建知识库请求")
public class LibraryCreateRequest {

    @Schema(description = "知识库名称", required = true, maxLength = 32)
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 32, message = "知识库名称最大32字符")
    private String name;

    @Schema(description = "知识库描述", maxLength = 255)
    @Size(max = 255, message = "知识库描述最大255字符")
    private String description;

    @Schema(description = "知识库类型: personal/team", required = true)
    @NotBlank(message = "知识库类型不能为空")
    private String type;

    @Schema(description = "封面图片URL")
    private String coverImage;
}
