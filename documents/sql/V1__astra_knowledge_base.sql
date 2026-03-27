-- Astra 知识库相关表
-- 包含：kb_library(知识库表)、kb_media(媒体文件表)、kb_chunk(分片向量表)

-- 1. 启用 pgvector 扩展（如果尚未启用）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 知识库表 kb_library
CREATE TABLE kb_library (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    type VARCHAR(16) NOT NULL DEFAULT 'personal',
    owner_id BIGINT NOT NULL,
    is_top BOOLEAN NOT NULL DEFAULT FALSE,
    cover_image VARCHAR(600),
    created_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_library_name_owner UNIQUE (name, owner_id),
    CONSTRAINT fk_library_owner FOREIGN KEY (owner_id) REFERENCES chat_session(user_id) ON DELETE CASCADE
);

COMMENT ON TABLE kb_library IS '知识库表';
COMMENT ON COLUMN kb_library.id IS '主键';
COMMENT ON COLUMN kb_library.name IS '知识库名称';
COMMENT ON COLUMN kb_library.description IS '知识库描述';
COMMENT ON COLUMN kb_library.type IS '知识库类型: personal/team';
COMMENT ON COLUMN kb_library.owner_id IS '所属用户ID';
COMMENT ON COLUMN kb_library.is_top IS '是否置顶';
COMMENT ON COLUMN kb_library.cover_image IS '封面图片地址';
COMMENT ON COLUMN kb_library.created_at IS '创建时间';
COMMENT ON COLUMN kb_library.updated_at IS '更新时间';

-- 3. 媒体文件表 kb_media
CREATE TABLE kb_media (
    id BIGSERIAL PRIMARY KEY,
    library_id BIGINT NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(2048) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    total_chunks INT4 DEFAULT 0,
    parsed_chunks INT4 DEFAULT 0,
    error_message VARCHAR(512),
    created_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_media_library FOREIGN KEY (library_id) REFERENCES kb_library(id) ON DELETE CASCADE,
    CONSTRAINT uk_media_sha256_library UNIQUE (sha256, library_id)
);

COMMENT ON TABLE kb_media IS '媒体表-存储知识库内上传的文件元信息';
COMMENT ON COLUMN kb_media.id IS '主键';
COMMENT ON COLUMN kb_media.library_id IS '所属知识库ID';
COMMENT ON COLUMN kb_media.file_name IS '原始文件名';
COMMENT ON COLUMN kb_media.mime_type IS 'MIME类型';
COMMENT ON COLUMN kb_media.file_size IS '文件大小(字节)';
COMMENT ON COLUMN kb_media.storage_path IS '存储路径/OSS Key';
COMMENT ON COLUMN kb_media.sha256 IS '内容哈希用于去重';
COMMENT ON COLUMN kb_media.status IS '解析状态: PENDING/PARSING/PARSED/FAILED';
COMMENT ON COLUMN kb_media.total_chunks IS '总分片数';
COMMENT ON COLUMN kb_media.parsed_chunks IS '已解析分片数';
COMMENT ON COLUMN kb_media.error_message IS '错误信息';
COMMENT ON COLUMN kb_media.created_at IS '创建时间';
COMMENT ON COLUMN kb_media.updated_at IS '更新时间';

-- 4. 分片向量表 kb_chunk
CREATE TABLE kb_chunk (
    id BIGSERIAL PRIMARY KEY,
    library_id BIGINT NOT NULL,
    media_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR(1024),
    chunk_index INT4 NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chunk_library FOREIGN KEY (library_id) REFERENCES kb_library(id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_media FOREIGN KEY (media_id) REFERENCES kb_media(id) ON DELETE CASCADE
);

COMMENT ON TABLE kb_chunk IS '分片表-存储解析后的文本分片及向量嵌入';
COMMENT ON COLUMN kb_chunk.id IS '主键';
COMMENT ON COLUMN kb_chunk.library_id IS '所属知识库ID';
COMMENT ON COLUMN kb_chunk.media_id IS '关联媒体文件ID';
COMMENT ON COLUMN kb_chunk.content IS '原始文本内容';
COMMENT ON COLUMN kb_chunk.embedding IS '向量嵌入(1024维)';
COMMENT ON COLUMN kb_chunk.chunk_index IS '分片序号';
COMMENT ON COLUMN kb_chunk.metadata IS '扩展元信息(页码、来源文件、章节标题等)';
COMMENT ON COLUMN kb_chunk.created_at IS '创建时间';

-- 5. 创建索引
-- 知识库索引
CREATE INDEX idx_library_owner_id ON kb_library(owner_id);
CREATE INDEX idx_library_type ON kb_library(type);
CREATE INDEX idx_library_is_top ON kb_library(is_top);
CREATE INDEX idx_library_created_at ON kb_library(created_at);

-- 媒体文件索引
CREATE INDEX idx_media_library_id ON kb_media(library_id);
CREATE INDEX idx_media_status ON kb_media(status);
CREATE INDEX idx_media_sha256 ON kb_media(sha256);
CREATE INDEX idx_media_created_at ON kb_media(created_at);

-- 分片向量索引 (HNSW 索引用于加速相似度查询)
CREATE INDEX idx_chunk_library_id ON kb_chunk(library_id);
CREATE INDEX idx_chunk_media_id ON kb_chunk(media_id);
CREATE INDEX idx_chunk_embedding ON kb_chunk USING hnsw (embedding vector_cosine_ops);

-- 6. 创建更新时间戳触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 7. 为表添加更新时间戳触发器
CREATE TRIGGER update_kb_library_updated_at
    BEFORE UPDATE ON kb_library
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_kb_media_updated_at
    BEFORE UPDATE ON kb_media
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
