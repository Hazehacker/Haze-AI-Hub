package top.hazenix.hazeaihub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: 聊天请求DTO
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "聊天请求参数")
public class ChatRequestDTO {

    @Schema(description = "用户输入内容", required = true)
    @NotBlank(message = "输入内容不能为空")
    private String prompt;

    @Schema(description = "会话ID（首条消息传null）")
    private Long sessionId;

    @Schema(description = "分组ID")
    private Long groupId;

    @Schema(description = "是否启用思考过程", defaultValue = "true")
    private Boolean enableThinking = true;

    @Schema(description = "思考token预算")
    private Integer thinkingBudget;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "会话类型", defaultValue = "chat")
    private String type = "chat";
}
