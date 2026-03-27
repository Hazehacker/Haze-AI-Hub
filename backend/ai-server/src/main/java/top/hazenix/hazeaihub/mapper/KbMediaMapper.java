package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.hazenix.hazeaihub.entity.KbMedia;

import java.util.List;
import java.util.Optional;

/**
 * 媒体文件 Mapper
 */
@Mapper
public interface KbMediaMapper extends BaseMapper<KbMedia> {

    /**
     * 获取知识库下的媒体文件列表
     * @param libraryId 知识库ID
     * @param status 状态筛选(可选)
     * @return 媒体文件列表
     */
    @Select("SELECT * FROM kb_media WHERE library_id = #{libraryId} AND (%s) ORDER BY created_at DESC")
    List<KbMedia> listByLibraryId(@Param("libraryId") Long libraryId, @Param("status") String status);

    /**
     * 检查文件是否已存在(通过SHA256)
     * @param libraryId 知识库ID
     * @param sha256 文件哈希
     * @return 存在的媒体文件
     */
    @Select("SELECT * FROM kb_media WHERE library_id = #{libraryId} AND sha256 = #{sha256} LIMIT 1")
    Optional<KbMedia> findByLibraryIdAndSha256(@Param("libraryId") Long libraryId, @Param("sha256") String sha256);

    /**
     * 获取媒体文件详情
     * @param id 媒体ID
     * @return 媒体文件信息
     */
    @Select("SELECT * FROM kb_media WHERE id = #{id}")
    Optional<KbMedia> findById(@Param("id") Long id);
}
