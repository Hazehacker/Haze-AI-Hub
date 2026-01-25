package top.hazenix.hazeaihub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.hazenix.hazeaihub.dto.ModelDTO;
import top.hazenix.hazeaihub.result.Result;
import top.hazenix.hazeaihub.service.IModelService;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 模型管理接口
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/25
 */
@RestController
@RequestMapping("/api/v1/ai/models")
@RequiredArgsConstructor
@Tag(name = "模型管理", description = "模型信息相关接口")
public class ModelController {

    private final IModelService modelService;
    /**
     * 获取可用模型列表
     * @return
     */
    @GetMapping
    @Operation(summary = "获取可用模型列表", description = "返回所有可用的AI模型列表")
    public Result<List<ModelDTO>> getModelList() {
        List<ModelDTO> models = modelService.listModels();
        
        return Result.success(models);
    }

    /**
     * 新增模型信息
     * @param modelDTO
     * @return
     */
    @PostMapping
    @Operation(summary = "新增模型信息", description = "新增模型信息")
    public Result<ModelDTO> addModel(@Validated @RequestBody ModelDTO modelDTO) {
        modelService.addModel(modelDTO);
        return Result.success(modelDTO);
    }

    /**
     * 删除模型信息
     * @param id
     * @return
     */
    @DeleteMapping
    @Operation(summary = "删除模型信息", description = "删除模型信息")
    public Result<Void> deleteModel(@RequestParam Long id) {
        modelService.deleteModel(id);
        return Result.success();
    }


}
