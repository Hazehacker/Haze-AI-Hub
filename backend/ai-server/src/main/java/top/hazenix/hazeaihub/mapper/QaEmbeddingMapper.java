package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.hazenix.hazeaihub.entity.KbQaEmbedding;

import java.util.List;

/**
 * QA向量 Mapper
 */
@Mapper
public interface QaEmbeddingMapper extends BaseMapper<KbQaEmbedding> {

    /**
     * 获取QA对的所有向量
     * @param qaPairId QA对ID
     * @return 向量列表
     */
    @Select("SELECT * FROM kb_qa_embedding WHERE qa_pair_id = #{qaPairId}")
    List<KbQaEmbedding> listByQaPairId(@Param("qaPairId") Long qaPairId);

    /**
     * 删除媒体文件的所有QA向量
     * @param mediaId 媒体ID
     */
    @Select("DELETE FROM kb_qa_embedding WHERE qa_pair_id IN " +
            "(SELECT id FROM kb_qa_pair WHERE media_id = #{mediaId})")
    void deleteByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 删除知识库的所有QA向量
     * @param libraryId 知识库ID
     */
    @Select("DELETE FROM kb_qa_embedding WHERE qa_pair_id IN " +
            "(SELECT id FROM kb_qa_pair WHERE library_id = #{libraryId})")
    void deleteByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 向量相似度检索
     * @param libraryId 知识库ID
     * @param queryEmbedding 查询向量
     * @param topK 召回数量
     * @param efSearch HNSW ef_search 参数，配合 iterative_scan=relaxed_order 使用
     * @return 匹配的KbQaEmbedding列表
     */
    List<KbQaEmbedding> vectorSearch(@Param("libraryId") Long libraryId,
                                     @Param("queryEmbedding") float[] queryEmbedding,
                                     @Param("topK") int topK,
                                     @Param("efSearch") int efSearch);

    /**
     * 批量插入QA向量
     * @param embeddings 向量列表
     */
    void batchInsert(@Param("embeddings") List<KbQaEmbedding> embeddings);
}
