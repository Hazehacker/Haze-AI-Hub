-- 混合检索 RRF 下推到 PostgreSQL 所需的数据库对象

-- 1. RRF 评分函数：将排名转换为 RRF 分数
--    公式: 1.0 / (rank + rrf_k)
--    rank 从 1 开始，rrf_k 为平滑因子（默认 60）
CREATE OR REPLACE FUNCTION rrf_score(rank bigint, rrf_k int DEFAULT 60)
RETURNS numeric
LANGUAGE SQL
IMMUTABLE PARALLEL SAFE
AS $$
    SELECT COALESCE(1.0 / ($1 + $2), 0.0);
$$;

COMMENT ON FUNCTION rrf_score(bigint, int) IS 'RRF (Reciprocal Rank Fusion) 评分函数，用于混合检索结果融合';

-- 2. GIN 全文索引：加速 BM25 关键词检索
--    使用 simple 分词器（适用于中文场景，不做词干提取）
CREATE INDEX IF NOT EXISTS idx_chunk_content_fulltext
    ON kb_chunk USING GIN (to_tsvector('simple', content));
