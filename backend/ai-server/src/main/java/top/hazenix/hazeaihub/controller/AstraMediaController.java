package top.hazenix.hazeaihub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.result.Result;
import top.hazenix.hazeaihub.service.IAstraMediaService;
import top.hazenix.hazeaihub.service.SseEmitterService;
import top.hazenix.hazeaihub.vo.MediaResponse;

import java.io.IOException;
import java.util.List;

/**
 * Astra 媒体文件 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/astra")
@RequiredArgsConstructor
@Tag(name = "Astra 媒体文件接口")
public class AstraMediaController {

    private final IAstraMediaService mediaService;
    private final SseEmitterService sseEmitterService;

    @PostMapping("/media")
    @Operation(summary = "上传文件")
    public Result<MediaResponse> uploadFile(
            @Parameter(description = "文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "知识库ID", required = true) @RequestParam("libraryId") Long libraryId) throws IOException {
        Long userId = BaseContext.getCurrentId();
        log.info("上传文件: userId={}, libraryId={}, fileName={}",
                userId, libraryId, file.getOriginalFilename());
        MediaResponse response = mediaService.uploadFile(libraryId, userId, file);
        return Result.success(response);
    }

    @GetMapping("/libraries/{libraryId}/media")
    @Operation(summary = "获取知识库下的文件列表")
    public Result<List<MediaResponse>> listMedia(
            @PathVariable Long libraryId,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer size) {
        Long userId = BaseContext.getCurrentId();
        List<MediaResponse> list = mediaService.listMedia(libraryId, userId, status, page, size);
        return Result.success(list);
    }

    @GetMapping("/media/{id}")
    @Operation(summary = "获取文件详情")
    public Result<MediaResponse> getMedia(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        MediaResponse response = mediaService.getMedia(id, userId);
        return Result.success(response);
    }

    @GetMapping("/media/{id}/status")
    @Operation(summary = "获取文件解析状态")
    public Result<MediaResponse> getMediaStatus(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        MediaResponse response = mediaService.getMediaStatus(id, userId);
        return Result.success(response);
    }

    @DeleteMapping("/media/{id}")
    @Operation(summary = "删除文件")
    public Result<Void> deleteMedia(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        mediaService.deleteMedia(id, userId);
        return Result.success();
    }

    @GetMapping("/media/{mediaId}/stream")
    @Operation(summary = "订阅解析进度", description = "SSE 端点")
    public SseEmitter subscribeParseProgress(@PathVariable Long mediaId) {
        Long userId = BaseContext.getCurrentId();
        log.info("SSE连接建立: mediaId={}, userId={}", mediaId, userId);

        // 这个接口安全要求低，暂时不做权限处理

        return sseEmitterService.createEmitter(mediaId);
    }
}
