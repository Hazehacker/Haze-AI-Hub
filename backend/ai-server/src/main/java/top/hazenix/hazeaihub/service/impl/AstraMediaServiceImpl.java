package top.hazenix.hazeaihub.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.hazenix.hazeaihub.entity.KbLibrary;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.entity.MediaStatus;
import top.hazenix.hazeaihub.enums.ErrorCode;
import top.hazenix.hazeaihub.exception.BusinessException;
import top.hazenix.hazeaihub.mapper.KbLibraryMapper;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.service.IAstraMediaService;
import top.hazenix.hazeaihub.utils.Sha256Util;
import top.hazenix.hazeaihub.vo.MediaResponse;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 媒体文件服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AstraMediaServiceImpl implements IAstraMediaService {

    private final KbMediaMapper mediaMapper;
    private final KbLibraryMapper libraryMapper;

    // 允许的文件类型
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "audio/mpeg",
            "audio/wav",
            "application/x-xmind",
            "text/plain"
    );

    // 文件大小限制 (100MB)
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    @Override
    @Transactional
    public MediaResponse uploadFile(Long libraryId, Long userId, MultipartFile file) {
        log.info("上传文件: libraryId={}, userId={}, fileName={}",
                libraryId, userId, file.getOriginalFilename());

        // 校验知识库存在和权限
        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_NOT_FOUND);
        }
        if (!library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限上传到此知识库");
        }

        // 校验文件类型
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(ErrorCode.ASTRA_UNSUPPORTED_FILE_TYPE,
                    "不支持的文件类型: " + mimeType);
        }

        // 校验文件大小
        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.ASTRA_FILE_SIZE_EXCEEDED,
                    "文件大小超出限制，最大100MB");
        }

        // 计算 SHA256
        String sha256;
        try {
            sha256 = Sha256Util.calculate(file.getInputStream());
        } catch (Exception e) {
            log.error("计算SHA256失败", e);
            throw new BusinessException(ErrorCode.ASTRA_SHA256_MISMATCH, "文件校验失败");
        }

        // 检查重复文件
        Optional<KbMedia> existing = mediaMapper.findByLibraryIdAndSha256(libraryId, sha256);
        if (existing.isPresent()) {
            log.info("文件已存在: sha256={}", sha256);
            return toResponse(existing.get());
        }

        // TODO: 上传到 OSS (暂用本地存储)
        String storagePath = "astra/" + libraryId + "/" + sha256;

        // 创建媒体记录
        KbMedia media = KbMedia.builder()
                .libraryId(libraryId)
                .fileName(file.getOriginalFilename())
                .mimeType(mimeType)
                .fileSize(fileSize)
                .storagePath(storagePath)
                .sha256(sha256)
                .status(MediaStatus.PENDING.getCode())
                .totalChunks(0)
                .parsedChunks(0)
                .build();

        mediaMapper.insert(media);
        log.info("媒体记录创建成功: id={}", media.getId());

        // TODO: 发送解析消息到 Redis Stream

        return toResponse(media);
    }

    @Override
    public List<MediaResponse> listMedia(Long libraryId, Long userId, String status, Integer page, Integer size) {
        // 校验权限
        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_NOT_FOUND);
        }
        if (!library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问此知识库");
        }

        List<KbMedia> mediaList = mediaMapper.listByLibraryId(libraryId, status);

        return mediaList.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MediaResponse getMedia(Long mediaId, Long userId) {
        KbMedia media = mediaMapper.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASTRA_MEDIA_NOT_FOUND));

        // 校验权限
        KbLibrary library = libraryMapper.selectById(media.getLibraryId());
        if (library == null || !library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问此文件");
        }

        return toResponse(media);
    }

    @Override
    public MediaResponse getMediaStatus(Long mediaId, Long userId) {
        KbMedia media = mediaMapper.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASTRA_MEDIA_NOT_FOUND));

        // 校验权限
        KbLibrary library = libraryMapper.selectById(media.getLibraryId());
        if (library == null || !library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问此文件");
        }

        return toResponse(media);
    }

    @Override
    @Transactional
    public void deleteMedia(Long mediaId, Long userId) {
        KbMedia media = mediaMapper.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASTRA_MEDIA_NOT_FOUND));

        // 校验权限
        KbLibrary library = libraryMapper.selectById(media.getLibraryId());
        if (library == null || !library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限删除此文件");
        }

        // 删除分片记录
        // TODO: 删除 OSS 文件

        // 删除媒体记录
        mediaMapper.deleteById(mediaId);
        log.info("媒体文件删除成功: id={}", mediaId);
    }

    @Override
    public Long checkDuplicate(Long libraryId, String sha256) {
        return mediaMapper.findByLibraryIdAndSha256(libraryId, sha256)
                .map(KbMedia::getId)
                .orElse(null);
    }

    private MediaResponse toResponse(KbMedia media) {
        return MediaResponse.builder()
                .id(media.getId())
                .libraryId(media.getLibraryId())
                .fileName(media.getFileName())
                .mimeType(media.getMimeType())
                .fileSize(media.getFileSize())
                .storagePath(media.getStoragePath())
                .sha256(media.getSha256())
                .status(media.getStatus())
                .totalChunks(media.getTotalChunks())
                .parsedChunks(media.getParsedChunks())
                .errorMessage(media.getErrorMessage())
                .createdAt(media.getCreatedAt())
                .updatedAt(media.getUpdatedAt())
                .build();
    }
}
