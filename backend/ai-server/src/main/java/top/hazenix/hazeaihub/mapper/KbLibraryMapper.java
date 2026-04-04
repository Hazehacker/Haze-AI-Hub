package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.hazenix.hazeaihub.entity.KbLibrary;

import java.util.List;

/**
 * 知识库 Mapper
 */
@Mapper
public interface KbLibraryMapper extends BaseMapper<KbLibrary> {

    /**
     * 获取用户知识库列表(带统计)
     * @param ownerId 用户ID
     * @param keyword 搜索关键字
     * @return 知识库列表
     */
    @Select("""
            SELECT l.*,
                   COUNT(DISTINCT m.id) as media_count,
                   COUNT(DISTINCT c.id) as chunk_count
            FROM kb_library l
            LEFT JOIN kb_media m ON l.id = m.library_id AND m.status = 'PARSED'
            LEFT JOIN kb_chunk c ON l.id = c.library_id
            WHERE l.owner_id = #{ownerId}
            AND (l.name LIKE '%' || #{keyword} || '%' OR l.description LIKE '%' || #{keyword} || '%')
            GROUP BY l.id
            ORDER BY l.is_top DESC, l.created_at DESC
            """)
    List<KbLibrary> listByOwnerWithStats(@Param("ownerId") Long ownerId, @Param("keyword") String keyword);

    /**
     * 获取知识库详情(带统计)
     * @param id 知识库ID
     * @return 知识库信息
     */
    @Select("""
            SELECT l.*,
                   COUNT(DISTINCT m.id) as media_count,
                   COUNT(DISTINCT c.id) as chunk_count
            FROM kb_library l
            LEFT JOIN kb_media m ON l.id = m.library_id AND m.status = 'PARSED'
            LEFT JOIN kb_chunk c ON l.id = c.library_id
            WHERE l.id = #{id}
            GROUP BY l.id
            """)
    KbLibrary getByIdWithStats(@Param("id") Long id);
}
