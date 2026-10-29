# 玩家档案卡片：本地预览

保留线上卡片的头像、玩家信息和纵向统计表。统计表共七行：累计在线时间、走过的总路程、累计挖掘、累计合成、钓鱼次数、村民交易次数、鞘翅飞行距离。
下方的「成就」固定为两行三列，每格依次显示成就图片、名称与等级、条内带百分比的十格经验条。
统计不可用时仍使用原有四行不可用提示，不把缺失数据显示成零。

预览直接调用插件的统计模型和 Java2D 渲染器，无需启动 Minecraft。
玩家样本、头像和生成图片放在仓库外，不提交玩家资料。

```sh
./gradlew previewInfoCard \
  -Ppreview-stats=/path/to/player-stats.json \
  -Ppreview-avatar=/path/to/avatar.png \
  -Ppreview-name=PlayerName \
  '-Ppreview-last-join=2026-10-29 12:16:00' \
  -Ppreview-output=/path/to/card.png
```

`preview-avatar` 可省略，显示头像暂不可用；预览不查询在线状态，只展示给定的最近上线时间。
输入统计是含顶层 `stats` 字段的原版玩家 JSON。在线玩家的实际插件查询也依赖最近一次落盘值，不能保证逐秒刷新。

## 统计口径与成就规则

- 在线时间使用 `minecraft:play_time`，按每秒 20 tick 换算。
- 步行里程保持原有 `minecraft:walk_one_cm` 口径，不混入冲刺、鞘翅或载具；飞行使用 `minecraft:aviate_one_cm`，原始单位均为厘米。
- 挖掘和合成分别累计原版 `minecraft:mined`、`minecraft:crafted` 分组中的数值；合成统计为产出数量。
- 钓鱼使用 `minecraft:fish_caught`，代表钓到鱼的次数，不含垃圾或宝藏；交易使用 `minecraft:traded_with_villager`。
- 六种成就始终展示，依次为挖掘、合成、飞行、钓鱼、交易、步行。尚未达标时显示灰色图案与「未解锁」。

默认门槛根据 2026-10-30 读取的 1,796 份玩家统计校准，随后按用户反馈适度提高铜级并重新回算。算法和达标比例见 [校准报告](achievement-calibration-status.md)：

| 类别 | 铜 | 银 | 金 |
| --- | ---: | ---: | ---: |
| 挖掘 | 500 | 36,000 | 680,000 |
| 合成 | 300 | 31,000 | 1,200,000 |
| 鞘翅飞行 | 8 km | 360 km | 5,100 km |
| 钓鱼 | 5 | 98 | 1,100 |
| 村民交易 | 25 | 390 | 18,000 |
| 步行 | 5 km | 120 km | 1,500 km |

进度为当前等级到下一等级的区间进度：`(当前值 - 本级门槛) / (下级门槛 - 本级门槛)`。未解锁时计算到铜级的进度，刚升级归零，金级固定 100%。
百分比最多保留两位小数并向下截断；点亮格数按原始比值向下取整，每完成 10% 点亮一格，无半格填充。
距离进度用原始厘米计算，不先取整公里。等级随当前门槛实时计算，不持久化成就状态。

## 门槛配置

`config.yml` 会自动补齐 `achievement-thresholds` 配置段。六类配置键依次为 `mining`、`crafting`、`elytra`、`fishing`、`trading`、`walking`，每类包含 `bronze`、`silver`、`gold` 三个累计门槛。例如：

```yaml
achievement-thresholds:
  trading:
    bronze: 25
    silver: 390
    gold: 18000
```

距离两类的配置单位为整数公里，其余为整数数量或次数。完整默认配置见 [achievement-thresholds-default.yml](achievement-thresholds-default.yml)。
必须满足 `0 < bronze < silver < gold`；距离最高为 21,474 km，钓鱼/交易最高为 2,147,483,647 次，避免配置出原版统计永远达不到的等级。挖掘和合成使用多个统计项的 long 汇总。

修改后运行 `/deerling reload`。无效成就门槛会在修改任何运行配置之前被拒绝，继续使用原配置，并给出具体字段错误。
有效重载后，新查询使用完整的新门槛快照，已经在途的查询继续使用它开始时的快照。
提高或降低门槛会影响已有玩家的当前等级；删除某字段会恢复该字段的插件默认值。

## 组件与美术

- `StatisticsTableComponent` 复用七行的图标、标签、右对齐数值和分隔线。
- `AchievementGridComponent` 排列六个 `AchievementComponent`。
- 成就由图片生成工具制作的三张等级边框和六张主题图案组成，位于 `assets/achievement-components/`，运行时叠加组合。高等级通过边框的装饰和材质区分。
- `ExperienceBarComponent` 只用 `assets/experience/` 中的空槽与点亮覆盖层两张像素素材，拼出十格、十一种填充状态；百分比在条内居中绘制。
- 美术素材固定缓存，不按玩家缓存整张图片；未解锁的灰色图层也只预计算一次。
- 卡片排版、文字和统计表的小型线条图标仍用 Java2D 绘制。成就边框与主题图案使用生成素材。
- 中文字体为 Noto Sans CJK SC 子集，沿用原 OFL 许可，并包含新界面的字形。

`previewInfoCard` 只在本地渲染输入样本，不连接服务器或发送机器人消息。
