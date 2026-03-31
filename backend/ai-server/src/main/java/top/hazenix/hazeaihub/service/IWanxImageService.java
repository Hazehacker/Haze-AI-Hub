package top.hazenix.hazeaihub.service;

import top.hazenix.hazeaihub.service.result.WanxImageResult;

public interface IWanxImageService {
    WanxImageResult generateImage(String prompt, Long sessionId);
}