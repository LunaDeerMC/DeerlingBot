---
name: onebot-11
description: 'OneBot v11 protocol workflow for DeerlingBot. Use when implementing or fixing OneBot/OneBot11/CQHTTP compatible WebSocket API calls, event parsing, message segments, group notices, private messages, send_group_msg, send_private_msg, or mapping protocol docs to this Java plugin.'
argument-hint: '要处理的 OneBot v11 任务，例如：新增 notice 事件、补充消息段解析、实现某个 API 调用、核对上报字段'
---

# OneBot v11 For DeerlingBot

## When to Use

- 需要根据 `./onebot-11-ref` 实现或修复 OneBot v11 协议逻辑
- 需要新增或调整消息事件、通知事件、元事件的解析
- 需要新增或调整发送动作，例如 `send_group_msg`、`send_private_msg`、`set_group_card`
- 需要补充消息段支持，例如 `text`、`at`、`reply`、`image`
- 需要把 OneBot v11 文档中的字段映射到 DeerlingBot 当前 Java 结构

## What This Skill Covers

- 正向 WebSocket 的 `/api`、`/event`、`/` 三种入口
- 公开 API 的参数和返回结构
- 消息事件、通知事件、元事件的关键字段
- 常用消息段格式和实现注意点
- DeerlingBot 中协议入口、事件模型、消息段模型、出站操作类的落点

## Workflow

### 1. 先判断任务属于哪一类

- 出站调用：新增或修复 API 请求体，修改 `protocols/GroupOperation.java` 或 `protocols/PrivateOperation.java`
- 入站事件：新增或修复事件解析，修改 `managers/CoreConnector.java` 与 `protocols/events/**`
- 消息段：新增或修复消息段编解码，修改 `protocols/segments/**`
- 协议核对：先查 [协议速查表](./references/protocol-cheatsheet.md)，再回到实现代码核对字段

### 2. 核对文档中的协议面

优先确认以下内容：

- 该能力属于 WebSocket API 还是事件上报
- action 名称是否和规范一致
- `params` 的必填字段、默认值、字段类型是否一致
- 入站 JSON 的 `post_type`、二级类型字段和子类型字段分别是什么
- 消息内容是字符串还是数组消息段

如果文档没有要求扩展行为，就不要自行引入协议外字段。

### 3. 映射到 DeerlingBot 的代码结构

当前仓库的 OneBot v11 处理模式如下：

- `managers/CoreConnector.java`
  - 负责接收 WebSocket 文本帧、解析 JSON、按 `post_type` 分发
  - 收到事件后转换成 Bukkit Event 并触发 `call()`
- `protocols/events/**`
  - `AbstractPost` 是所有上报事件的基类
  - `message/*`、`notice/*` 按 OneBot 事件类型拆分类
- `protocols/segments/**`
  - 每个消息段对应一个 `MessageSegment` 子类
  - `MessageSegment.parse(...)` 负责把数组消息段解析成具体对象
- `protocols/GroupOperation.java` 与 `protocols/PrivateOperation.java`
  - 负责构造 `{"action": ..., "params": ...}` 的出站 JSON
  - 最终统一交给 `CoreConnector.send(...)`

### 4. 实现时遵循当前仓库约定

- 使用 `fastjson2` 的 `JSONObject` 和 `JSONArray`
- 日志使用 `XLogger`，不要使用 `System.out`
- 新事件尽量保持“一种 OneBot 类型对应一个明确的 Java 类”
- 新增调度逻辑时使用仓库自己的 `Scheduler`
- 面向 QQ 侧的用户可见文本保持简体中文

### 5. 修改后的核查清单

- action、字段名、字段类型与 `./onebot-11-ref` 一致
- `CoreConnector` 的分发条件覆盖到了新的 `post_type` 或二级类型
- 新事件类的 `parse` 方法完整读取了必需字段
- 新消息段既考虑出站 `parse()`，也考虑入站 `MessageSegment.parse(...)`
- 对暂不支持的字段或消息段，显式说明而不是静默伪装支持

## Decision Notes

### 新增 API 调用时

- 如果本质是“发送消息”，优先沿用现有 `MessageSegment...` 组包方式
- 如果是管理类动作，按 OneBot action 原名新增 PascalCase 静态方法
- 若响应值后续需要被业务消费，考虑补充 echo 或统一响应处理；如果当前仓库仍是 fire-and-forget，则保持现状，不要半套改造

### 新增事件时

- 先在 OneBot 文档里确认 `post_type`、二级类型、子类型
- 再在 `CoreConnector` 中加入分发
- 最后在 `protocols/events/...` 中增加 `parse(JSONObject)` 和字段访问器

### 新增消息段时

- 先确认协议里该段是否支持“收”、“发”或两者都支持
- 只实现仓库真正需要的方向；如果只支持发送，不要伪造接收解析
- 如果当前仓库解析返回 `null`，新增后要检查上层调用是否能正确处理非空对象

## Known Repo-Specific Gaps

根据当前仓库实现，以下点在做协议工作时需要特别注意：

- `CoreConnector` 当前主要分发 `message`、部分 `notice` 和部分 `meta_event`
- `MessageSegment.parse(...)` 目前只明确支持 `at`、`text`、`reply`，`image` 接收解析尚未完成
- `GroupOperation` / `PrivateOperation` 当前主要关注发包，不处理统一响应模型

做扩展时应优先补齐这些真实缺口，而不是只改文档表面。

## Reference

- [协议速查表](./references/protocol-cheatsheet.md)
