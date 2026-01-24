package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.hazenix.hazeaihub.entity.ChatSession;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 获取用户会话列表
     * @param userId 当前用户ID
     * @param limit 返回数量限制
     * @return 会话列表
     */
    List<ChatSession> list(Long userId, Integer limit);
}

