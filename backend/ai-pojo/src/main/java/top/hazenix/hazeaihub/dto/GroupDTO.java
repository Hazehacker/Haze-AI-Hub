package top.hazenix.hazeaihub.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDTO {
    @Schema(description = "分组id[主键]", example = "1")
    private Long id;

    @Schema(description = "用户id", example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "分组名称", example = "测试分组")
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 50, message = "分组名称长度不能超过50")
    private String name;

    @Schema(description = "分组排序", example = "0")
    private Integer sort;


}
