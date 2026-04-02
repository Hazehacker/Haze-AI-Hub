package top.hazenix.hazeaihub.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {
    @Schema(description = "附件id[主键]")
    private Long id;

    @Schema(description = "消息id[外键]")
    @NotNull(message = "所属消息ID不能为空")
    private Long messageId;

    @Schema(description = "附件原始名称")
    private String fileName;

    @Schema(description = "附件类型")
    private String mimeType;

    @Schema(description = "附件大小")
    private Long fileSize;

    @Schema(description = "存储路径URL")
    private String storagePath;

    @Schema(description = "内容哈希")
    @TableField(value = "sha256")
    private String contentHash;

    @Schema(description = "来源类型: wanx_flux, upload, etc.")
    private String sourceType;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
