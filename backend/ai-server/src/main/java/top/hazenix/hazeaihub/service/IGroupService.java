package top.hazenix.hazeaihub.service;

import jakarta.validation.Valid;
import top.hazenix.hazeaihub.dto.GroupDTO;
import top.hazenix.hazeaihub.entity.Group;

import java.util.List;

public interface IGroupService {
    /**
     * 新增分组
     */
    void addGroup(GroupDTO groupDTO);

    /**
     * 查询分组(无参，当前用户)
     */
    List<Group> queryGroup();

    /**
     * 删除分组
     */
    void deleteGroup(Long id);

    /**
     * 修改分组信息（名称，排序号）
     */
    void updateGroup(Long id, @Valid GroupDTO groupDTO);
}
