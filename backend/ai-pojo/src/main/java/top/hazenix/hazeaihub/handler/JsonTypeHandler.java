package top.hazenix.hazeaihub.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@Slf4j
@MappedTypes({Map.class})
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<Map<String, Object>>() {};

    static {
        objectMapper = new ObjectMapper();
        // 注册JavaTimeModule以支持Java 8日期时间类型（如Duration）
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            // PostgreSQL 需要显式地将字符串转换为 jsonb 类型
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(objectMapper.writeValueAsString(parameter));
            ps.setObject(i, jsonObject);
        } catch (JsonProcessingException e) {
            log.error("Error converting Map to JSON string", e);
            throw new SQLException("Error converting Map to JSON string", e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getObject(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getObject(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getObject(columnIndex));
    }

    private Map<String, Object> parseJson(Object jsonObject) throws SQLException {
        if (jsonObject == null) {
            return null;
        }
        
        try {
            String json;
            // 处理PGobject类型
            if (jsonObject instanceof PGobject) {
                json = ((PGobject) jsonObject).getValue();
            } else {
                json = jsonObject.toString();
            }
            
            if (json == null || json.isEmpty() || "null".equals(json)) {
                return null;
            }
            
            return objectMapper.readValue(json, TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON to Map: {}", jsonObject, e);
            throw new SQLException("Error parsing JSON to Map", e);
        }
    }
}
