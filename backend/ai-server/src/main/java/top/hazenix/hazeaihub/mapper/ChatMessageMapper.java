package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.hazenix.hazeaihub.entity.ChatMessage;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    /**
     * 根据会话ID获取消息列表（按创建时间升序）
     * @param sessionId 会话ID
     * @param limit 限制数量
     * @return 消息列表
     */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY created_at ASC LIMIT #{limit}")
    List<ChatMessage> selectBySessionIdOrderByCreatedAt(@Param("sessionId") Long sessionId, @Param("limit") Integer limit);
}
