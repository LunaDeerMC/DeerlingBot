# 成就组合素材

由图片生成工具制作，六种主题图案与五级边框分别复用。等级顺序为木、铁、铜、钻、金。

- `frame-wood`、`frame-iron`、`frame-copper`、`frame-diamond`、`frame-gold`：用户确认的 1254×1254 RGBA PNG 共享等级边框（2026-11-09）。原稿完整保留；首次加载时缩放到 512×512 缓存，后续卡片复用。
- `content-pickaxe`、`content-cube`、`content-wing`、`content-fish`、`content-trade`、`content-route`：六种成就主题，256×256 RGBA PNG。

`AchievementComponent` 先画边框，再居中叠加主题图案；未解锁状态复用灰度木级边框与主题素材。不要在此存放玩家数据、头像或整张卡片。
