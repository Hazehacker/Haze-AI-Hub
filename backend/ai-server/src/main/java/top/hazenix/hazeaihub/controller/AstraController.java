package top.hazenix.hazeaihub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.dto.AstraChatRequest;
import top.hazenix.hazeaihub.dto.LibraryCreateRequest;
import top.hazenix.hazeaihub.dto.LibraryUpdateRequest;
import top.hazenix.hazeaihub.result.Result;
import top.hazenix.hazeaihub.service.IAstraLibraryService;
import top.hazenix.hazeaihub.service.IAstraSearchService;
import top.hazenix.hazeaihub.vo.LibraryResponse;

import java.util.List;

/**
 * Astra 知识库 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/astra")
@RequiredArgsConstructor
@Tag(name = "Astra 知识库接口")
public class AstraController {

    private final IAstraLibraryService libraryService;
    private final IAstraSearchService searchService;

    // ==================== 知识库管理 ====================

    @PostMapping("/libraries")
    @Operation(summary = "创建知识库")
    public Result<LibraryResponse> createLibrary(@Valid @RequestBody LibraryCreateRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("创建知识库: userId={}, name={}", userId, request.getName());
        LibraryResponse response = libraryService.createLibrary(userId, request);
        return Result.success(response);
    }

    @GetMapping("/libraries")
    @Operation(summary = "获取知识库列表")
    public Result<List<LibraryResponse>> listLibraries(
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer size) {
        Long userId = BaseContext.getCurrentId();
        List<LibraryResponse> list = libraryService.listLibraries(userId, keyword, page, size);
        return Result.success(list);
    }

    @GetMapping("/libraries/{id}")
    @Operation(summary = "获取知识库详情")
    public Result<LibraryResponse> getLibrary(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        LibraryResponse response = libraryService.getLibrary(id, userId);
        return Result.success(response);
    }

    @PutMapping("/libraries/{id}")
    @Operation(summary = "更新知识库")
    public Result<LibraryResponse> updateLibrary(
            @PathVariable Long id,
            @Valid @RequestBody LibraryUpdateRequest request) {
        Long userId = BaseContext.getCurrentId();
        LibraryResponse response = libraryService.updateLibrary(id, userId, request);
        return Result.success(response);
    }

    @DeleteMapping("/libraries/{id}")
    @Operation(summary = "删除知识库")
    public Result<Void> deleteLibrary(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        libraryService.deleteLibrary(id, userId);
        return Result.success();
    }

    @PutMapping("/libraries/{id}/toggle-top")
    @Operation(summary = "置顶/取消置顶知识库")
    public Result<LibraryResponse> toggleTop(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        LibraryResponse response = libraryService.toggleTop(id, userId);
        return Result.success(response);
    }

    // ==================== 知识问答 ====================

    @PostMapping(value = "/chat", produces = "text/event-stream")
    @Operation(summary = "知识问答", description = "流式响应接口")
    public Flux<String> chat(@Valid @RequestBody AstraChatRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("收到知识问答请求: userId={}, libraryId={}, prompt={}",
                userId, request.getLibraryId(), request.getPrompt());
        return searchService.chat(userId, request);
    }
}
