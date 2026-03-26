package top.hazenix.hazeaihub.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: 聊天响应VO（用于SSE事件）
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "聊天响应")
public class ChatResponseVO {

    @Schema(description = "会话ID（首条消息时返回）")
    private Long sessionId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "事件类型：session-created/thinking/answer/complete")
    private String eventType;
}
