package top.hazenix.hazeaihub.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @ApiModelProperty(value = "消息id[主键]", required = true)
    @NotBlank(message = "主键ID不能为空")
    private Long id;

    @ApiModelProperty(value = "会话id[外键]", required = true)
    @NotBlank(message = "所属会话ID不能为空")
    private Long sessionId;

    @ApiModelProperty(value = "消息角色(类型)[user/assistant/system]")
    private String role;

    @ApiModelProperty(value = "消息内容")
    private String content;

    @ApiModelProperty(value = "扩展信息[模型名、token统计、thinking片段汇总、解析到的“原谅值”等]")
    private Map<String, Object> metadataJson;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

}
