package top.hazenix.hazeaihub.service;

/**
 * QA对生成服务接口
 */
public interface IQaPairGenerationService {

    /**
     * 为媒体文件生成QA对
     * @param mediaId 媒体文件ID
     */
    void generateQaPairs(Long mediaId);

    /**
     * 为单个Chunk生成QA对
     * @param chunkId Chunk ID
     */
    void generateQaPairForChunk(Long chunkId);
}
