package top.hazenix.hazeaihub.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.constant.MessageConstant;
import top.hazenix.hazeaihub.context.BaseContext;
import top.hazenix.hazeaihub.dto.ModelDTO;
import top.hazenix.hazeaihub.entity.Model;
import top.hazenix.hazeaihub.mapper.ModelMapper;


import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import top.hazenix.hazeaihub.constant.CacheConstants;
import top.hazenix.hazeaihub.utils.CacheUtil;

@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements IModelService{
    private final ModelMapper modelMapper;
    private final CacheUtil cacheUtil;

    @Value("${haze.admin.id:1}")
    private Long adminId;

    @Override
    public void addModel(ModelDTO modelDTO) {
        // 参数校验
        if(modelDTO.getIsBeta() == null){
            modelDTO.setIsBeta(false);
        }
        if(modelDTO.getIsRecommended() == null){
            modelDTO.setIsRecommended(false);
        }
        if(modelDTO.getStatus() ==  null){
            modelDTO.setStatus(true);
        }
        if(modelDTO.getSort() == null){
            modelDTO.setSort(0);
        }

        Model model = BeanUtil.copyProperties(modelDTO, Model.class);
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        modelMapper.insert(model);

        // 清除缓存
        cacheUtil.delete(CacheConstants.CAFFEINE_MODEL_LIST, CacheConstants.MODEL_LIST_KEY);
    }

    @Override
    public List<ModelDTO> listModels() {
        return cacheUtil.queryWithPassThrough(
                CacheConstants.CAFFEINE_MODEL_LIST,
                CacheConstants.MODEL_LIST_KEY,
                new com.fasterxml.jackson.core.type.TypeReference<List<ModelDTO>>() {},
                this::listModelsFromDB,
                CacheConstants.BASE_TTL_HOURS,
                TimeUnit.HOURS
        );
    }

    private List<ModelDTO> listModelsFromDB() {
        List<Model> models = modelMapper.selectList(new LambdaQueryWrapper<Model>()
                .eq(Model::getStatus, true)
                .orderByDesc(Model::getSort)
        );
        if (models == null) {
            throw new RuntimeException("模型列表为空");
        }
        return BeanUtil.copyToList(models, ModelDTO.class);
    }

    @Override
    public void deleteModel(Long id) {
        // 权限校验，只有管理员能操作
        if(!BaseContext.getCurrentId().equals(adminId)) {
            throw new RuntimeException(MessageConstant.NOT_AUTHED_TO_DELETE);
        }
        modelMapper.deleteById(id);

        // 清除缓存
        cacheUtil.delete(CacheConstants.CAFFEINE_MODEL_LIST, CacheConstants.MODEL_LIST_KEY);
    }
}
