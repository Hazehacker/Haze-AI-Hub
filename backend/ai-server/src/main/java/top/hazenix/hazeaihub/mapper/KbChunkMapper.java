package top.hazenix.hazeaihub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
    List<KbChunk> listByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 获取知识库的所有分片
     * @param libraryId 知识库ID
     * @return 分片列表
     */
    List<KbChunk> listByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 删除媒体文件的所有分片
     * @param mediaId 媒体ID
     */
    void deleteByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 删除知识库的所有分片
     * @param libraryId 知识库ID
     */
    void deleteByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 获取分片数量
     * @param libraryId 知识库ID
     * @return 分片总数
     */
    long countByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 向量相似度检索
     * @param libraryId 知识库ID
     * @param queryEmbedding 查询向量
     * @param topK 召回数量
     * @param efSearch HNSW ef_search 参数，配合 iterative_scan=relaxed_order 使用
     * @return 包含KbChunk和similarity的Map列表
     */
    List<Map<String, Object>> vectorSearch(@Param("libraryId") Long libraryId,
                                           @Param("queryEmbedding") float[] queryEmbedding,
                                           @Param("topK") int topK,
                                           @Param("efSearch") int efSearch);

    /**
     * 批量插入KbChunk
     * @param chunks 分片列表
     */
    void batchInsert(@Param("chunks") List<KbChunk> chunks);

    /**
     * BM25 全文检索（使用 PostgreSQL ts_rank_cd）
     * @param libraryId 知识库ID
     * @param queryTerms 搜索关键词（空格分隔）
     * @param topK 召回数量
     * @return 包含KbChunk和bm25_score的Map列表
     */
    List<Map<String, Object>> bm25Search(@Param("libraryId") Long libraryId,
                                          @Param("queryTerms") String queryTerms,
                                          @Param("topK") int topK);

    /**
     * 混合检索 RRF：单条 SQL 完成 BM25 + 向量双路召回 + Reciprocal Rank Fusion 合并
     * <p>将双路召回和 RRF 融合全部下推到 PostgreSQL 执行，减少 DB 往返次数和应用层合并开销</p>
     *
     * @param libraryId      知识库ID
     * @param queryEmbedding 查询向量
     * @param queryTerms     搜索关键词（空格分隔）
     * @param vectorTopK     向量检索召回数量
     * @param bm25TopK       BM25 检索召回数量
     * @param efSearch       HNSW ef_search 参数
     * @param rrfK           RRF 平滑因子（通常为 60）
     * @param topK           最终输出数量
     * @return 包含 KbChunk、rrf_score、vector_score、bm25_score 的 Map 列表
     */
    List<Map<String, Object>> hybridSearchRrf(@Param("libraryId") Long libraryId,
                                               @Param("queryEmbedding") float[] queryEmbedding,
                                               @Param("queryTerms") String queryTerms,
                                               @Param("vectorTopK") int vectorTopK,
                                               @Param("bm25TopK") int bm25TopK,
                                               @Param("efSearch") int efSearch,
                                               @Param("rrfK") int rrfK,
                                               @Param("topK") int topK);
}