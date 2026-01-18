package top.hazenix.hazeaihub.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.hazenix.hazeaihub.dto.GroupDTO;
import top.hazenix.hazeaihub.entity.Group;
import top.hazenix.hazeaihub.result.Result;
import top.hazenix.hazeaihub.service.IGroupService;

import java.util.List;

/**
 * @description: 分组相关接口
 * @author: Hazenix
 * @version: 0.0.1
 * @date: 2026/1/18
 * @return
 */
@RestController
@RequestMapping("/api/v1/group")
@RequiredArgsConstructor
@Slf4j
@Api("分组相关接口")
public class GroupController {
    private final IGroupService groupService;
    /**
     * @description: 新增分组
     * @param: GroupDTO
     * @version: 0.0.1
     * @return
     */
    @PostMapping
    @ApiOperation("新增分组")
    public Result addGroup(@Valid @RequestBody GroupDTO groupDTO) {
        log.info("新增分组:{}", groupDTO);
        groupService.addGroup(groupDTO);
        return Result.success();
    }

    /**
     * @description: 查询分组（查询当前用户所有未删除的分组）
     * @param:
     * @version: 0.0.1
     * @return
     */
    @GetMapping
    @ApiOperation("查询分组")
    public Result queryGroup() {
        log.info("查询分组");
        List<Group> list = groupService.queryGroup();
        return Result.success(list);
    }


    /**
     * @description: 根据groupId删除分组
     * @param:
     * @version: 0.0.1
     * @return
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除分组")
    public Result deleteGroup(@PathVariable Long id) {
        log.info("删除分组:{}", id);
        groupService.deleteGroup(id);
        return Result.success();
    }

    /**
     * @description: 修改分组
     * @param:
     * @version: 0.0.1
     * @return
     */
    @PutMapping("/{id}")
    @ApiOperation("修改分组")
    public Result updateGroup(@PathVariable Long id, @Valid @RequestBody GroupDTO groupDTO) {
        log.info("修改分组:{}", groupDTO);
        groupService.updateGroup(id, groupDTO);
        return Result.success();
    }


}
