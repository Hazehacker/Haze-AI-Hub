package top.hazenix.hazeaihub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @ApiModelProperty(value = "消息id[主键]", required = true)
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "会话id[外键]", required = true)
    @NotNull(message = "所属会话ID不能为空")
    private Long sessionId;

    @ApiModelProperty(value = "消息角色(类型)[user/assistant/system]")
    private String role;

    @ApiModelProperty(value = "消息内容")
    private String content;

    @ApiModelProperty(value = "扩展信息[模型名、token统计、thinking片段汇总、解析到的'原谅值'等]")
    @TableField(typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> metadataJson;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * JSONB 类型处理器，用于 PostgreSQL 的 JSONB 字段
     */
    public static class JsonbTypeHandler implements TypeHandler<Map<String, Object>> {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public void setParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
            try {
                if (parameter == null) {
                    ps.setString(i, null);
                } else {
                    ps.setString(i, objectMapper.writeValueAsString(parameter));
                }
            } catch (Exception e) {
                throw new SQLException("Error converting Map to JSON string", e);
            }
        }

        @Override
        public Map<String, Object> getResult(ResultSet rs, String columnName) throws SQLException {
            String json = rs.getString(columnName);
            return parseJson(json);
        }

        @Override
        public Map<String, Object> getResult(ResultSet rs, int columnIndex) throws SQLException {
            String json = rs.getString(columnIndex);
            return parseJson(json);
        }

        @Override
        public Map<String, Object> getResult(CallableStatement cs, int columnIndex) throws SQLException {
            String json = cs.getString(columnIndex);
            return parseJson(json);
        }

        private Map<String, Object> parseJson(String json) {
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                throw new RuntimeException("Error parsing JSON string to Map", e);
            }
        }
    }
}
