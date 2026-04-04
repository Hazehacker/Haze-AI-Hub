package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
    List<KbLibrary> listByOwnerWithStats(@Param("ownerId") Long ownerId, @Param("keyword") String keyword);

    /**
     * 获取知识库详情(带统计)
     * @param id 知识库ID
     * @return 知识库信息
     */
    KbLibrary getByIdWithStats(@Param("id") Long id);
}