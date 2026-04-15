-- V3: zhparser 中文分词全文检索支持

-- 1. 创建 zhparser 文本搜索配置（中文分词）
-- 注意：需要先安装 zhparser 扩展 CREATE EXTENSION IF NOT EXISTS zhparser;
CREATE TEXT SEARCH CONFIGURATION IF NOT EXISTS zhcfg (parser = zhparser);

-- 2. 将分词结果映射到 simple 词典（去停用词、小写化）
ALTER TEXT SEARCH CONFIGURATION zhcfg ADD MAPPING FOR a,i,l,n,v,e WITH simple;

-- 3. 新增 search_vector 列（存储时由 DB 自动生成 tsvector）
ALTER TABLE kb_chunk ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- 4. 建立 GIN 索引加速 search_vector 检索（覆盖原有 idx_chunk_content_fulltext）
DROP INDEX IF EXISTS idx_chunk_content_fulltext;
CREATE INDEX IF NOT EXISTS idx_chunk_search_vector ON kb_chunk USING GIN (search_vector);

-- 5. 创建生成 search_vector 的触发器函数（入库/更新时自动更新）
CREATE OR REPLACE FUNCTION update_search_vector()
RETURNS trigger AS $$
BEGIN
    -- 调用 zhcfg 配置进行中文分词，生成 tsvector
    -- 如果 zhparser 不可用，则设为 NULL（触发器不会报错）
    BEGIN
        NEW.search_vector := to_tsvector('zhcfg', COALESCE(NEW.content, ''));
    EXCEPTION WHEN OTHERS THEN
        NEW.search_vector := NULL;
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 6. 创建触发器（插入和更新时自动调用）
DROP TRIGGER IF EXISTS trg_update_search_vector ON kb_chunk;
CREATE TRIGGER trg_update_search_vector
    BEFORE INSERT OR UPDATE OF content ON kb_chunk
    FOR EACH ROW
    EXECUTE FUNCTION update_search_vector();

-- 7. 为存量数据生成分词向量（zhparser 可用时执行，禁用事务以避免锁）
DO $$
DECLARE
    _can_parse boolean;
BEGIN
    -- 检查 zhparser 是否可用
    SELECT EXISTS(SELECT 1 FROM pg_proc WHERE proname = 'zhcfg') INTO _can_parse;
    IF _can_parse THEN
        UPDATE kb_chunk SET search_vector = to_tsvector('zhcfg', content)
        WHERE search_vector IS NULL;
        RAISE NOTICE '存量数据 search_vector 已生成';
    ELSE
        RAISE NOTICE 'zhparser 不可用，请安装后手动执行: UPDATE kb_chunk SET search_vector = to_tsvector(''zhcfg'', content) WHERE search_vector IS NULL';
    END IF;
END $$;
