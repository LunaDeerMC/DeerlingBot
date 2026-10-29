# 成就组合素材

由图片生成工具制作透明素材，再从九宫格中提取为 256×256 RGBA PNG。

- `frame-bronze`、`frame-silver`、`frame-gold`：共享等级边框。
- `content-pickaxe`、`content-cube`、`content-wing`、`content-fish`、`content-trade`、`content-route`：六种成就主题。

`AchievementComponent` 先画边框，再居中叠加主题图案；未解锁状态复用灰度素材。不要在此存放玩家数据、头像或整张卡片。
