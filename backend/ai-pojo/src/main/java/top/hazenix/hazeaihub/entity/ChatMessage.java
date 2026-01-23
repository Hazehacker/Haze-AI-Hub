package top.hazenix.hazeaihub.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Schema(description = "消息id[主键]")
    private Long id;

    @Schema(description = "会话id[外键]")
    @NotNull(message = "所属会话ID不能为空")
    private Long sessionId;

    @Schema(description = "消息角色(类型)[U/A/system]")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息状态[true:正常 | false:已删除]")
    private Boolean status;

    @Schema(description = "扩展信息[模型名、token统计、thinking片段汇总、解析到的原谅值等]")
    @TableField(value = "metadata_json", typeHandler = top.hazenix.hazeaihub.handler.JsonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> metadataJson;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

}
