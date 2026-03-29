package top.hazenix.hazeaihub.service;

import org.springframework.web.multipart.MultipartFile;
import top.hazenix.hazeaihub.vo.MediaResponse;

import java.io.IOException;
import java.util.List;

/**
 * 媒体文件服务接口
 */
public interface IAstraMediaService {

    /**
     * 上传文件到知识库
     * @param libraryId 知识库ID
     * @param userId 用户ID
     * @param file 上传的文件
     * @return 媒体文件响应
     */
    MediaResponse uploadFile(Long libraryId, Long userId, MultipartFile file) throws IOException;

    /**
     * 获取知识库下的文件列表
     * @param libraryId 知识库ID
     * @param userId 用户ID
     * @param status 状态筛选(可选)
     * @param page 页码
     * @param size 每页大小
     * @return 文件列表
     */
    List<MediaResponse> listMedia(Long libraryId, Long userId, String status, Integer page, Integer size);

    /**
     * 获取文件详情
     * @param mediaId 文件ID
     * @param userId 用户ID
     * @return 媒体文件响应
     */
    MediaResponse getMedia(Long mediaId, Long userId);

    /**
     * 获取文件解析状态
     * @param mediaId 文件ID
     * @param userId 用户ID
     * @return 媒体文件响应(仅包含状态信息)
     */
    MediaResponse getMediaStatus(Long mediaId, Long userId);

    /**
     * 删除文件(级联清理分片和OSS文件)
     * @param mediaId 文件ID
     * @param userId 用户ID
     */
    void deleteMedia(Long mediaId, Long userId);

    /**
     * 检查SHA256是否已存在
     * @param libraryId 知识库ID
     * @param sha256 文件哈希
     * @return 已存在的媒体文件ID，如果不存在返回null
     */
    Long checkDuplicate(Long libraryId, String sha256);
}
