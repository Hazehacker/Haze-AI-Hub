package top.hazenix.hazeaihub.service;

import top.hazenix.hazeaihub.service.result.IntentDetectionResult;

public interface IIntentDetectionService {
    IntentDetectionResult analyzeIntent(String userInput);
}