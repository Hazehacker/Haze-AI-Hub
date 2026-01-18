package top.hazenix.hazeaihub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.dto.GroupDTO;
import top.hazenix.hazeaihub.entity.Group;
import top.hazenix.hazeaihub.mapper.GroupMapper;
import top.hazenix.hazeaihub.service.IGroupService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements IGroupService {
    private final GroupMapper groupMapper;

    @Override
    public void addGroup(GroupDTO groupDTO) {
        // 设置sort默认值
        if(groupDTO.getSort() == null) {
            groupDTO.setSort(0);
        }
        Group group = Group.builder()
                .userId(groupDTO.getUserId())
                .name(groupDTO.getName())
                .sort(groupDTO.getSort())
                .status(true)
                .createdAt(LocalDateTime.now())
                .build();
        groupMapper.insert(group);
    }

    @Override
    public List<Group> queryGroup() {
        Long currentId = BaseContext.getCurrentId();

        return groupMapper.selectList(
                new LambdaQueryWrapper<Group>()
                        .eq(Group::getUserId, currentId)
                        .orderByDesc(Group::getSort)
                );
    }

    @Override
    public void deleteGroup(Long id) {
        // 权限校验
        if(!BaseContext.getCurrentId().equals(groupMapper.selectById(id).getUserId())) {
            throw new RuntimeException("无权限删除改分组");
        }

        groupMapper.deleteById(id);
    }

    @Override
    public void updateGroup(Long id, GroupDTO groupDTO) {
        // 权限校验
        if(!BaseContext.getCurrentId().equals(groupMapper.selectById(id).getUserId())) {
            throw new RuntimeException("无权限修改该分组");
        }

        LambdaUpdateWrapper<Group> updateWrapper = new LambdaUpdateWrapper<Group>()
                .eq(Group::getId, id)
                .set(Group::getName, groupDTO.getName())
                .set(Group::getSort, groupDTO.getSort());
        
        groupMapper.update(updateWrapper);
    }
}
