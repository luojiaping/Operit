---
title: DeepSeek 数据服务
status: complete
---

# DeepSeek 数据服务

## 计划

- 使用 `ModelConfigManager` 读取 DeepSeek 配置和指定 API Key，不触发 Key 轮换
- 使用宿主网络客户端请求余额和平台用量接口
- 使用现有 `TokenUsageRepository`、`TokenStatsQueryService`、`TokenCostCalculator` 和 `TokenPriceResolver`
- 增加最新 usage 查询和独立余额快照存储，不修改现有 Room schema
- 由宿主 Android Keystore-backed encrypted preferences 保存平台凭据，禁止向日志暴露原文

## 完成记录

平台 Token 使用 `EncryptedSharedPreferences` 保存。余额接口由宿主 OkHttp 调用，Token usage 使用现有 Room 聚合，余额差值使用独立 SharedPreferences 快照，不增加 Room migration。
