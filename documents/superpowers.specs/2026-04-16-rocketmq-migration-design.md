# RocketMQ 迁移设计文档

## 1. 背景

Astra 知识库模块当前使用 Redis Stream 实现文件解析任务的异步消息处理。为了学习 RocketMQ 的使用方式，计划在独立分支 `feat/rocketmq` 中使用 RocketMQ 重写，删除 Redis Stream 相关代码。

## 2. 分支策略

| 分支 | 内容 |
|-----|------|
| `main` | 保持 Redis Stream 实现，不变 |
| `feat/rocketmq` | 使用 RocketMQ 重写，删除 Redis Stream |

## 3. 架构设计

### 生产者

使用 Spring Boot 官方 `RocketMQTemplate`（来自 `spring-boot-starter-rocketmq`）：

```java
@Resource
private RocketMQTemplate rocketMQTemplate;

public void sendParseTask(ParseMessage msg) {
    rocketMQTemplate.convertAndSend("astra-parse-topic", msg);
}
```

### 消费者

使用 `@RocketMQMessageListener` 注解方式，Spring Boot 自动管理线程池：

```java
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "astra-parse-topic",
    consumerGroup = "astra-parse-consumer-group"
)
public class RocketMQParseConsumer implements RocketMQListener<ParseMessage> {

    private final FileParserFactory parserFactory;
    private final KbMediaMapper mediaMapper;
    private final SseEmitterService sseEmitterService;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(ParseMessage msg) {
        log.info("收到解析任务: mediaId={}", msg.getMediaId());
        // 业务处理...
        // 失败时抛出异常，RocketMQ 自动重试（可配置重试次数）
    }
}
```

## 4. 重试与死信处理

RocketMQ 原生支持消息重试：
- 消费失败抛出异常即可触发自动重试
- 重试次数用 `@RocketMQMessageListener` 的 `maxRetryCount` 配置
- 超过最大重试后消息自动移入 `%DLQ%` 前缀的死信 Topic

```yaml
rocketmq:
  consumer:
    maxRetryCount: 3
```

死信 Topic 命名规则：`%DLQ%{topic}`，例如 `%DLQ%astra-parse-topic`

## 5. 依赖变更

删除：
- Redisson 相关依赖

新增：
- `spring-boot-starter-rocketmq`
- `rocketmq-client`

## 6. 配置变更

新增 `application-rocketmq.yaml` 或直接在原配置中替换：

```yaml
spring:
  rocketmq:
    namesrv-addr: localhost:9876
    producer:
      group: astra-parse-producer-group

astra:
  rocketmq:
    topic: astra-parse-topic
    consumer-group: astra-parse-consumer-group
```

## 7. 实施步骤

1. 创建分支 `feat/rocketmq`
2. 删除 RedissonStreamProducer、AstraParseConsumer
3. 删除 Redis Stream 相关依赖和配置
4. 添加 RocketMQ 依赖
5. 新建 RocketMQParseConsumer（注解方式）
6. 新建 RocketMQProducerService（或直接用 RocketMQTemplate）
7. 更新 AstraMediaServiceImpl 注入
8. 测试验证

## 8. Topic 创建

切换前需在 RocketMQ 中创建 Topic：

```bash
mqadmin updateTopic -n localhost:9876 -t astra-parse-topic -c DefaultCluster
```
