# DeerlingBot 图片信息卡（Java2D）

自 2.3-beta Java2D 改造分支起，图片版 `/info` 直接使用 Java2D 在内存中绘制，不依赖 Selenium、Chrome、ChromeDriver、HTML/CSS 模板或 `DeerlingBot_resources` 仓库。

## 功能与结构

- `InfoCardRenderer`：固定 V5 样式的玩家卡片。输出 1440 × 1656 的 ARGB 图片；标题为“白鹿原玩家档案”，右上角没有独立“白鹿原”文案；左上角使用内置服务器 Logo，累计挖掘使用普通镐子图标。
- `assets/bailuyuan-logo.png` 与 `assets/fonts/`：随 JAR 分发，保持各宿主机的字体效果一致。随字体附带 OFL 授权说明。
- `InfoImageService`：后台单线程生成图片，最多 4 项排队，不持有大尺寸图片缓存；插件重载或停用时取消待执行任务。不要在 Folia 区域线程或 Bukkit 主线程上同步绘图。
- `AvatarManager`：异步获取 Mineatar 真实 Minecraft 玩家头像（玩家 UUID）。连接限时 2.5 秒，接收响应体的空闲限时 2.5 秒（只在响应头到达后开始计时），排队和下载总计不超过 6 秒。成功头像新鲜缓存 10 分钟，随后最多保留 12 小时以应对暂时性网络失败；从未成功取得头像时，Java2D 显示明确的中性“暂不可用”占位图，不再使用虚构的 Minecraft 玩家脸。
- `PlayerSnapshotService`：产出不可变的昵称、在线状态、最近上线时间、在线时长、行走距离、累计挖掘和累计合成数据。数据采集与绘图彻底分离，避免跨 Folia 区域线程访问玩家对象。
- `FancyInfo`：将快照与头像组合后排入 Java2D 服务，编码和 OneBot 消息发送继续在异步任务中进行，重载后的旧请求不能发送。
- `fancyCommand`：保留原开关。启用时 `/info` 发送图片；关闭时沿用原文字版逻辑。**无需再手动下载任何浏览器资源**。

## 统计采集注意事项

在线玩家的 Bukkit 统计 / PAPI 通过所属实体线程采集；Folia 离线玩家从主世界原版统计 JSON 读取，支持 `world/stats/` 和 DeerFolia 使用的 `world/players/stats/` 两种布局。离线文件无法使用时回退到小型持久统计缓存，未知字段显示“暂不可用”。玩家存档为只读，不会被插件编辑。

## 构建、检查和回退

在开发机使用 Java 25 与缓存的 Gradle 9.4.1 运行 `gradle test jar --offline`。该项目的 Shadow 8.1.1 与 Gradle 9 存在兼容问题；Java 插件原生 `jar` 任务可构建 lite JAR，依赖由 `plugin.yml` 中的 `libraries` 声明提供。生成包应核对 `plugin.yml`、字体、Logo、Java2D 类和 Selenium 依赖是否已移除。

生产服升级应先在 DeerCloud 测试服验证；升级前备份原 JAR/配置，维护窗口正常重启才能加载新代码。**不要在生产服务运行时删除** `plugins/DeerlingBot/libs`、`plugins/DeerlingBot/templates` 或旧 JAR 回退副本。旧资源子模块已从源码解除，但独立 `DeerlingBot_resources` Git 仓库仍保留历史用途。

更新、回退操作不应影响 Minecraft 世界存档或 Velocity。
