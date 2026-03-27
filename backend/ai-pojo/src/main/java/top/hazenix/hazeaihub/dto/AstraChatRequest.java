package top.hazenix.hazeaihub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Astra 知识问答请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识问答请求")
public class AstraChatRequest {

    @Schema(description = "知识库ID", required = true)
    @NotNull(message = "知识库ID不能为空")
    private Long libraryId;

    @Schema(description = "会话ID，null表示新建会话")
    private Long sessionId;

    @Schema(description = "用户问题", required = true)
    @NotBlank(message = "问题不能为空")
    private String prompt;
}
