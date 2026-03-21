# OneBot v11 协议速查

本文件是给 DeerlingBot 使用的精简速查，不替代 `./onebot-11-ref` 原文。实现前如有歧义，以原文为准。

## 1. WebSocket 通信模型

OneBot v11 正向 WebSocket 提供三个入口：

- `/api`
  - 客户端发 JSON 请求调用 API
  - 基本格式：`{"action":"send_group_msg","params":{...},"echo":"any"}`
- `/event`
  - OneBot 主动推送事件上报
  - 上报结构与 HTTP POST 一致
- `/`
  - 同时承载 `/api` 和 `/event`

返回结果核心字段：

- `status`: `ok` 或 `failed`
- `retcode`: 失败时对应错误码；WebSocket 场景把 HTTP 状态码映射到 `1400`、`1401`、`1403`、`1404`
- `data`: 响应对象或 `null`
- `echo`: 原样回传

## 2. 常用公开 API

### 消息发送

- `send_private_msg`
  - 必填：`user_id`、`message`
  - 可选：`auto_escape`
  - 返回：`message_id`
- `send_group_msg`
  - 必填：`group_id`、`message`
  - 可选：`auto_escape`
  - 返回：`message_id`
- `send_msg`
  - 可用于统一入口，但当前仓库更适合继续分成 private/group 两条方法

### 常见群管理

- `set_group_card`
  - 必填：`group_id`、`user_id`
  - `card` 为空字符串表示删除群名片
- `set_group_kick`
  - 必填：`group_id`、`user_id`
  - 可选：`reject_add_request`
- `set_group_ban`
  - 必填：`group_id`、`user_id`
  - `duration=0` 表示取消禁言

### 常见查询

- `get_login_info`
- `get_group_info`
- `get_group_member_info`
- `get_group_list`

如果仓库需要消费响应数据，而不是仅发送动作，应补充响应建模或至少处理 `echo` 关联。

## 3. 关键事件结构

### 私聊消息

核心字段：

- `post_type = "message"`
- `message_type = "private"`
- `sub_type = "friend" | "group" | "other"`
- `message_id`
- `user_id`
- `message`
- `raw_message`
- `sender`

### 群消息

核心字段：

- `post_type = "message"`
- `message_type = "group"`
- `sub_type = "normal" | "anonymous" | "notice"`
- `group_id`
- `user_id`
- `anonymous`
- `message`
- `raw_message`
- `sender`

需要特别注意：

- 匿名消息依赖 `anonymous.flag`，后续禁言匿名成员时要用到
- `sender` 字段是尽力提供，不保证总是完整或绝对准确

### 通知与元事件

当前仓库已经明显接入过的通知类包括：

- `group_admin`
- `group_increase`
- `group_decrease`
- `group_ban`

元事件里，当前实现已显式观察：

- `meta_event_type = "lifecycle"` 且 `sub_type = "connect"`
- `meta_event_type = "heartbeat"` 的日志处理

新增其它 notice/request/meta 事件时，先补 `CoreConnector` 分发，再补具体事件类。

## 4. 常用消息段

### `text`

```json
{
  "type": "text",
  "data": {
    "text": "纯文本内容"
  }
}
```

### `at`

```json
{
  "type": "at",
  "data": {
    "qq": "10001000"
  }
}
```

`qq = "all"` 表示全体成员。

### `image`

```json
{
  "type": "image",
  "data": {
    "file": "http://example.com/1.jpg"
  }
}
```

发送时 `file` 可来自：

- 已接收的文件名
- `file:///` 绝对路径 URI
- `http://` 或 `https://` URL
- `base64://` 内容

### `reply`

回复消息段依赖原消息 ID。实现时要确认是发包支持、收包支持，还是双向都支持。

## 5. DeerlingBot 里的映射落点

### 入口与分发

- `src/main/java/cn/lunadeer/mc/deerlingbot/managers/CoreConnector.java`
  - 负责接收 WebSocket 文本消息
  - 读取 `post_type`
  - 按 `message_type`、`notice_type`、`meta_event_type` 分发

### 事件模型

- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/events/message/`
- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/events/notice/`

OneBot 上报在这里被转成 Bukkit Event，供 `@EventHandler` 监听。

### 出站动作

- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/GroupOperation.java`
- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/PrivateOperation.java`

当前风格：

- 使用 PascalCase 静态方法名
- action 名保持 OneBot 原名字符串
- `params.message` 走数组消息段，不是 CQ 码字符串

### 消息段

- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/segments/MessageSegment.java`
- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/segments/TextSegment.java`
- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/segments/MentionSegment.java`
- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/segments/ReplySegment.java`
- `src/main/java/cn/lunadeer/mc/deerlingbot/protocols/segments/ImageSegment.java`

当前已知状态：

- 收包解析明确支持 `text`、`at`、`reply`
- `image` 的接收解析仍未完成，现有 `parse(JSONObject)` 会返回 `null`

## 6. 修改清单

在这个仓库里做 OneBot v11 修改时，至少确认以下事项：

1. action、字段名、字段层级与 `./onebot-11-ref` 一致。
2. 入站事件的 `post_type` 和二级类型判断放在了正确位置。
3. 事件类里的字段类型和协议一致，尤其是 QQ 号、群号、时间戳。
4. 如果消息是数组消息段，不要错误退化成单字符串。
5. 对暂未支持的消息段或事件，宁可显式标注缺失，也不要制造“看起来能用”的空实现。
