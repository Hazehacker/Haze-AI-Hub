-- QA 对生成相关表
-- 包含：kb_qa_pair(QA对表)、kb_qa_embedding(QA向量表)
-- 依赖于 kb_chunk 表

-- 1. QA 对表 kb_qa_pair
CREATE TABLE kb_qa_pair (
    id BIGSERIAL PRIMARY KEY,
    chunk_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    library_id BIGINT NOT NULL,
    media_id BIGINT NOT NULL,
    created_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_qa_pair_chunk FOREIGN KEY (chunk_id) REFERENCES kb_chunk(id) ON DELETE CASCADE
    -- 逻辑外键: library_id 关联 kb_library(id)，media_id 关联 kb_media(id)，业务层保证
);

COMMENT ON TABLE kb_qa_pair IS 'QA对表-存储从Chunk生成的问答对';
COMMENT ON COLUMN kb_qa_pair.id IS '主键';
COMMENT ON COLUMN kb_qa_pair.chunk_id IS '关联的Chunk ID';
COMMENT ON COLUMN kb_qa_pair.question IS '生成的问题';
COMMENT ON COLUMN kb_qa_pair.answer IS '生成的答案(来自原文)';
COMMENT ON COLUMN kb_qa_pair.library_id IS '所属知识库ID';
COMMENT ON COLUMN kb_qa_pair.media_id IS '所属媒体文件ID';
COMMENT ON COLUMN kb_qa_pair.created_at IS '创建时间';

-- 2. QA 向量表 kb_qa_embedding
CREATE TABLE kb_qa_embedding (
    id BIGSERIAL PRIMARY KEY,
    qa_pair_id BIGINT NOT NULL,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_qa_embedding_qa_pair FOREIGN KEY (qa_pair_id) REFERENCES kb_qa_pair(id) ON DELETE CASCADE
);

COMMENT ON TABLE kb_qa_embedding IS 'QA向量表-存储QA对的向量嵌入';
COMMENT ON COLUMN kb_qa_embedding.id IS '主键';
COMMENT ON COLUMN kb_qa_embedding.qa_pair_id IS '关联的QA对ID';
COMMENT ON COLUMN kb_qa_embedding.embedding IS 'QA问题向量(1024维)';
COMMENT ON COLUMN kb_qa_embedding.created_at IS '创建时间';

-- 3. 创建索引
-- QA对索引
CREATE INDEX idx_qa_pair_chunk_id ON kb_qa_pair(chunk_id);
CREATE INDEX idx_qa_pair_library_id ON kb_qa_pair(library_id);
CREATE INDEX idx_qa_pair_media_id ON kb_qa_pair(media_id);

-- QA向量索引 (HNSW 索引用于加速相似度查询)
CREATE INDEX idx_qa_embedding_qa_pair_id ON kb_qa_embedding(qa_pair_id);
CREATE INDEX idx_qa_embedding_hnsw ON kb_qa_embedding USING hnsw (embedding vector_cosine_ops);

-- 4. 为 kb_media 表添加 qa_status 字段
ALTER TABLE kb_media ADD COLUMN qa_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

COMMENT ON COLUMN kb_media.qa_status IS 'QA生成状态: PENDING/GENERATING/GENERATED/FAILED';
