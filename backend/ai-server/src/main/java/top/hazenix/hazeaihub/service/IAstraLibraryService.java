package top.hazenix.hazeaihub.service;

import top.hazenix.hazeaihub.dto.LibraryCreateRequest;
import top.hazenix.hazeaihub.dto.LibraryUpdateRequest;
import top.hazenix.hazeaihub.entity.KbLibrary;
import top.hazenix.hazeaihub.vo.LibraryResponse;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface IAstraLibraryService {

    /**
     * 创建知识库
     * @param userId 用户ID
     * @param request 创建请求
     * @return 知识库响应
     */
    LibraryResponse createLibrary(Long userId, LibraryCreateRequest request);

    /**
     * 获取用户知识库列表
     * @param userId 用户ID
     * @param keyword 搜索关键字
     * @param page 页码
     * @param size 每页大小
     * @return 知识库列表
     */
    List<LibraryResponse> listLibraries(Long userId, String keyword, Integer page, Integer size);

    /**
     * 获取知识库详情
     * @param libraryId 知识库ID
     * @param userId 用户ID(用于权限校验)
     * @return 知识库响应
     */
    LibraryResponse getLibrary(Long libraryId, Long userId);

    /**
     * 更新知识库
     * @param libraryId 知识库ID
     * @param userId 用户ID
     * @param request 更新请求
     * @return 更新后的知识库响应
     */
    LibraryResponse updateLibrary(Long libraryId, Long userId, LibraryUpdateRequest request);

    /**
     * 删除知识库(硬删除，级联清理)
     * @param libraryId 知识库ID
     * @param userId 用户ID
     */
    void deleteLibrary(Long libraryId, Long userId);

    /**
     * 置顶/取消置顶知识库
     * @param libraryId 知识库ID
     * @param userId 用户ID
     * @return 更新后的知识库响应
     */
    LibraryResponse toggleTop(Long libraryId, Long userId);

    /**
     * 检查用户是否有权限访问知识库
     * @param libraryId 知识库ID
     * @param userId 用户ID
     * @return true 有权限
     */
    boolean hasAccess(Long libraryId, Long userId);
}
