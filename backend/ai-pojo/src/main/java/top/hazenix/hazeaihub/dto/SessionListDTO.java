package top.hazenix.hazeaihub.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: 会话列表DTO
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话列表项")
public class SessionListDTO {

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "会话类型")
    private String type;

    @Schema(description = "分组ID")
    private Long groupId;

    @Schema(description = "是否置顶")
    private Boolean isTop;

    @Schema(description = "最后活跃时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveAt;

    @Schema(description = "消息数量")
    private Integer messageCount;

    @Schema(description = "最后一条消息预览")
    private String lastMessagePreview;
}
