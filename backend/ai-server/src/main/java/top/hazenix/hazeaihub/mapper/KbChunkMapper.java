package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.hazenix.hazeaihub.entity.KbChunk;

import java.util.List;
import java.util.Map;

/**
 * 知识库分片 Mapper
 */
@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    /**
     * 获取媒体文件的所有分片
     * @param mediaId 媒体ID
     * @return 分片列表
     */
    @Select("SELECT * FROM kb_chunk WHERE media_id = #{mediaId} ORDER BY chunk_index")
    List<KbChunk> listByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 获取知识库的所有分片
     * @param libraryId 知识库ID
     * @return 分片列表
     */
    @Select("SELECT * FROM kb_chunk WHERE library_id = #{libraryId} ORDER BY media_id, chunk_index")
    List<KbChunk> listByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 删除媒体文件的所有分片
     * @param mediaId 媒体ID
     */
    @Select("DELETE FROM kb_chunk WHERE media_id = #{mediaId}")
    void deleteByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 删除知识库的所有分片
     * @param libraryId 知识库ID
     */
    @Select("DELETE FROM kb_chunk WHERE library_id = #{libraryId}")
    void deleteByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 获取分片数量
     * @param libraryId 知识库ID
     * @return 分片总数
     */
    @Select("SELECT COUNT(*) FROM kb_chunk WHERE library_id = #{libraryId}")
    long countByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 向量相似度检索
     * @param libraryId 知识库ID
     * @param queryEmbedding 查询向量
     * @param topK 召回数量
     * @return 包含KbChunk和similarity的Map列表
     */
    List<Map<String, Object>> vectorSearch(@Param("libraryId") Long libraryId,
                                           @Param("queryEmbedding") float[] queryEmbedding,
                                           @Param("topK") int topK);

    /**
     * 批量插入KbChunk
     * @param chunks 分片列表
     */
    void batchInsert(@Param("chunks") List<KbChunk> chunks);
}
