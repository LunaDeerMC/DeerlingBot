# 十格经验条素材

两张 16×16 RGBA 像素素材：`slot-empty.png` 是空槽，`slot-filled.png` 是透明背景的亮色覆盖层。
`ExperienceBarComponent` 用最近邻插值拼接十格，按完成的 10% 区间数覆盖前 N 格，并在条内绘制精确百分比。
