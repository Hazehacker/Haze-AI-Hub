# 迁移至 Spring AI Alibaba 的理由

## 1. 模型供应商长期锁定阿里云百炼

本项目的 AI 能力全部基于阿里云百炼平台（DashScope），包括对话模型（DeepSeek-R1、DeepSeek-V3、Qwen 系列）和向量模型（text-embedding-v4）。在可预见的未来，项目不存在切换至其他云厂商的计划。在此前提下，继续使用 Spring AI 原生框架并通过 OpenAI 兼容模式间接对接百炼，属于绕路方案，引入了不必要的适配层。Spring AI Alibaba 作为专为阿里云百炼设计的集成框架，能够更直接、更稳定地对接平台能力。

## 2. 思考模型支持需要手写大量代码

当前项目中 `BailianThinkingServiceProImpl` 完全绕开了 Spring AI 的抽象层，直接使用 `WebClient` 手动构造 HTTP 请求、解析 SSE 流、提取 `reasoning_content` 字段。这部分代码超过 200 行，包含手动重试、错误处理、流解析等大量样板代码，维护成本高且容易出错。Spring AI Alibaba 原生支持思考模型，内置对 `reasoning_content` 的解析，可以直接通过 `ThinkingOption` 等配置项启用，彻底消除这部分手写代码。

## 3. 多模态能力支持更完整

项目已有多模态对话的设计规划（`documents/多模态对话设计.md`），涉及图片、文件等附件类型（`Attachment` 实体已存在）。Spring AI 通过 OpenAI 兼容模式对阿里云多模态模型（如 `qwen2.5-vl`）的支持存在兼容性风险，而 Spring AI Alibaba 对百炼平台的多模态接口有原生适配，后续开发阻力更小。

## 4. 向量模型与 RAG 链路更顺畅

项目使用了 `text-embedding-v4` 向量模型配合 Redis 向量库实现 RAG（知识库问答，即 Astra 功能）。目前通过 OpenAI 兼容模式调用嵌入接口，存在潜在的参数不兼容问题。Spring AI Alibaba 对 DashScope 嵌入模型有原生支持，配置更简洁，也更容易利用百炼平台后续推出的新向量模型。

## 5. 配置更简洁，减少兼容层噪音

当前 `application.yaml` 中使用 `spring.ai.openai.base-url` 指向百炼的兼容地址，这种配置方式语义上容易造成混淆，也依赖百炼对 OpenAI 协议的兼容程度。切换后可以直接使用 `spring.ai.alibaba.dashscope` 相关配置，语义清晰，且不受 OpenAI 协议版本变更的影响。
