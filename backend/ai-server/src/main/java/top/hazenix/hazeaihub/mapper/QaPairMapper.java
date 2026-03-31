package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.hazenix.hazeaihub.entity.KbQaPair;

import java.util.List;

/**
 * QA对 Mapper
 */
@Mapper
public interface QaPairMapper extends BaseMapper<KbQaPair> {

    /**
     * 获取媒体文件的所有QA对
     * @param mediaId 媒体ID
     * @return QA对列表
     */
    @Select("SELECT * FROM kb_qa_pair WHERE media_id = #{mediaId}")
    List<KbQaPair> listByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 获取Chunk的所有QA对
     * @param chunkId Chunk ID
     * @return QA对列表
     */
    @Select("SELECT * FROM kb_qa_pair WHERE chunk_id = #{chunkId}")
    List<KbQaPair> listByChunkId(@Param("chunkId") Long chunkId);

    /**
     * 获取知识库的所有QA对
     * @param libraryId 知识库ID
     * @return QA对列表
     */
    @Select("SELECT * FROM kb_qa_pair WHERE library_id = #{libraryId}")
    List<KbQaPair> listByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 删除媒体文件的所有QA对
     * @param mediaId 媒体ID
     */
    @Select("DELETE FROM kb_qa_pair WHERE media_id = #{mediaId}")
    void deleteByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 删除知识库的所有QA对
     * @param libraryId 知识库ID
     */
    @Select("DELETE FROM kb_qa_pair WHERE library_id = #{libraryId}")
    void deleteByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 获取Chunk的QA对数量
     * @param chunkId Chunk ID
     * @return QA对数量
     */
    @Select("SELECT COUNT(*) FROM kb_qa_pair WHERE chunk_id = #{chunkId}")
    long countByChunkId(@Param("chunkId") Long chunkId);

    /**
     * 批量插入QA对
     * @param qaPairs QA对列表
     */
    void batchInsert(@Param("qaPairs") List<KbQaPair> qaPairs);
}
