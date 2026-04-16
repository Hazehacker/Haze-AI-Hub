package top.hazenix.hazeaihub.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import top.hazenix.hazeaihub.bo.ParseMessage;
import top.hazenix.hazeaihub.entity.KbMedia;
import top.hazenix.hazeaihub.entity.MediaStatus;
import top.hazenix.hazeaihub.mapper.KbMediaMapper;
import top.hazenix.hazeaihub.parser.FileParser;
import top.hazenix.hazeaihub.parser.FileParserFactory;
import top.hazenix.hazeaihub.service.SseEmitterService;
import top.hazenix.hazeaihub.vo.ChunkResponse;

import java.util.List;

/**
 * RocketMQ 解析消费者
 * 使用 @RocketMQMessageListener 注解方式，框架管理线程池
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = "astra-parse-topic",
    consumerGroup = "astra-parse-consumer-group"
)
@RequiredArgsConstructor
public class RocketMQParseConsumer implements RocketMQListener<ParseMessage> {

    private final KbMediaMapper mediaMapper;
    private final FileParserFactory parserFactory;
    private final SseEmitterService sseEmitterService;

    @Override
    public void onMessage(ParseMessage msg) {
        log.info("收到解析任务: mediaId={}", msg.getMediaId());

        try {
            // 更新媒体状态为 PARSING
            updateMediaStatus(msg.getMediaId(), MediaStatus.PARSING, null);
            sseEmitterService.sendProgress(msg.getMediaId(), 0, 0, 0);

            // 获取解析器
            FileParser parser = parserFactory.getParser(msg.getFileType());
            if (parser == null) {
                throw new RuntimeException("不支持的文件类型: " + msg.getFileType());
            }

            // 执行解析
            List<ChunkResponse> chunks = parser.parse(msg);

            // 更新解析完成状态
            int totalChunks = chunks.size();
            updateMediaStatus(msg.getMediaId(), MediaStatus.PARSED, null, totalChunks);
            sseEmitterService.sendComplete(msg.getMediaId(), totalChunks);

            log.info("解析任务完成: mediaId={}", msg.getMediaId());

        } catch (Exception e) {
            log.error("解析任务失败: mediaId={}", msg.getMediaId(), e);
            updateMediaStatus(msg.getMediaId(), MediaStatus.FAILED, e.getMessage());
            sseEmitterService.sendError(msg.getMediaId(), e.getMessage());
            // 抛出异常触发 RocketMQ 自动重试
            throw new RuntimeException("解析任务失败", e);
        }
    }

    private void updateMediaStatus(Long mediaId, MediaStatus status, String errorMessage) {
        updateMediaStatus(mediaId, status, errorMessage, 0);
    }

    private void updateMediaStatus(Long mediaId, MediaStatus status, String errorMessage, int totalChunks) {
        KbMedia media = mediaMapper.selectById(mediaId);
        if (media != null) {
            media.setStatus(status.getCode());
            if (errorMessage != null) {
                media.setErrorMessage(errorMessage);
            }
            if (totalChunks > 0) {
                media.setTotalChunks(totalChunks);
                media.setParsedChunks(totalChunks);
            }
            mediaMapper.updateById(media);
        }
    }
}