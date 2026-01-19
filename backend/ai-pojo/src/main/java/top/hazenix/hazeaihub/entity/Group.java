package top.hazenix.hazeaihub.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    @Schema(description = "分组id[主键]")
    private Long id;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "分组名称")
    private String name;

    @Schema(description = "分组排序")
    private Integer sort;

    @Schema(description = "分组状态[true:正常 | false:已删除]")
    private Boolean status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;


}
