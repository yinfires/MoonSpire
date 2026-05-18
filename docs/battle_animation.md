# Moon Spire 战斗动画实现说明

本文是战斗动画实现和维护的权威入口。任何新增、修改或删除战斗动画类型、时序、渲染钩子、客户端视觉状态、临时手持物、弹射物、受击/击退表现或怪物专属姿势时，都必须先阅读本文，并在同一变更中更新对应说明。

## 维护原则

- 服务端仍是战斗结算、目标选择、伤害/格挡/治疗、实体安全位置和最终状态的权威来源；客户端只负责平滑、姿势、临时手持物、粒子/光束/弹射物、飘字和视觉插值。
- 战斗世界动画使用客户端 tick 节奏推进，保持 20 TPS 语义。UI 卡牌动画可以使用渲染帧实时差值，但不要把原版物品使用、弓弩蓄力、临时手持物或实体位移动画改成渲染帧计时。
- 能复用原版状态时优先复用原版状态，例如 `startUsingItem`、`useItemRemaining`、弓/弩/三叉戟/药水使用姿势和怪物自身攻击姿势；不要在单帧渲染回调里临时 set/restore 手持物来伪造动画。
- 服务端可为结算节奏移动或锁定实体，但面向玩家的平滑位移、肢体摆动、后坐/击退偏移和姿势补偿应放在客户端视觉层，避免通过每 tick 服务端传送实现平滑效果。
- 新增 `BattleVisualEvent.AnimationType` 时，必须同时检查事件编码、服务端发射点、`ClientBattleState` 状态、`ClientEvents` 渲染/姿势桥、`BattleWorldOverlay` 世界渲染，以及本文的类型说明。

## 主链路

- `BattleState` 负责把卡牌结算拆成 `PendingCardBatch` 和可选 `BattleAnimation`。`beginBattleAnimation(...)` 决定使用近战突进、远程弹射物、药水、激流、守卫者光束、唤魔者法术或普通即时结算。
- `BattleState.emitVisual(...)` 把玩家可见信息写成 `BattleVisualEvent`：攻击者/目标实体 id、临时手持物、弹射物物品、展示卡牌、伤害/格挡/治疗数值、延迟、动画类型、动画时长、起点/命中点、击退向量和朝向点。
- `BattleSnapshot` 携带 visual events 到客户端。`ClientBattleState.setSnapshot(...)` 收到快照后排入 `pendingVisualEvents`，并提前把伤害、格挡和治疗数值加入飘字队列。
- `ClientBattleState.consumeVisualEvents()` 在客户端 tick 中按 `delayTicks` 消费事件，创建或更新 `VisualState`，同时建立格挡获得动画、守卫者光束、弹射物视觉和怪物出牌展示。
- `ClientEvents` 是实体渲染和原版状态桥：同步临时主手物品、原版使用物品状态、弓弩/三叉戟/药水姿势、怪物攻击姿势、激流旋转、自爆缩放、受击闪烁、视觉位移和走路动画。
- `BattleWorldOverlay` 绘制世界层内容：目标描边、守卫者光束、弹射物实体、伤害/格挡/治疗飘字、格挡获得图标和持续状态图标。

## 数据边界

- `BattleVisualEvent` 是服务端到客户端的动画协议。它记录的是“应该如何播放这次视觉反馈”，不是新的玩法规则；不要把只影响客户端表现的推导写回战斗结算。
- `animationStart` 和 `animationStrike` 是平滑位移和弹射物路径的关键坐标。近战类动画使用它们计算客户端视觉偏移，远程类动画使用它们计算飞行路径。
- `knockbackDelta` 只用于客户端受击/击退表现。实际战斗位置仍由服务端锁定、移动和安全位置逻辑控制。
- `lookTarget` 让攻击者在播放视觉事件时面向目标，避免客户端只靠当前实体朝向推断。
- `playedCard` 和 `itemStack` 负责卡牌/手持物展示。纯效果伤害、反伤、流血等没有实体动作的反馈可以使用 `AnimationType.NONE`，只播放飘字或命中反馈。

## 动画类型地图

- 近战位移：`MELEE_LUNGE`、`VINDICATOR_AXE_SWING`、`VEX_CHARGE_LUNGE`、`RAVAGER_HEAD_RAM`、`WARDEN_MELEE`、`PIGLIN_MELEE_SWING`、`HOGLIN_HEAD_ATTACK` 由 `BattleState.LungeAnimation` 和 `LungeStyle` 控制服务端节奏；客户端 `VisualState` 用相同阶段时长生成渲染偏移和走路速度，`ClientEvents` 同步对应怪物攻击姿势。劫掠兽、疣猪兽和监守者的原版攻击姿态只在准备与接近阶段结束、实体已经停在 `lungeStrike` 后启动，避免边靠近边提前攻击。
- 激流突进：`RIPTIDE_RUSH` 使用服务端 `RiptideRushAnimation` 安排蓄力、冲刺和停顿；客户端以三叉戟临时手持物、使用状态、`autoSpinAttack` 视觉状态和中心高度偏移播放旋转冲刺。
- 远程弹射物：`BOW_DRAW`、`CROSSBOW_LOAD`、`TRIDENT_THROW`、`CHANNELING_TRIDENT_THROW`、`POTION_THROW`、`WIND_CHARGE`、`BLAZE_FIREBALL`、`GHAST_FIREBALL`、`SHULKER_BULLET` 使用准备时间加飞行时间。服务端在命中节奏点结算效果，客户端 `ProjectileVisual` 负责从起点飞向命中点，必要时生成命中特效或视觉闪电。
- 药水投掷音效：`POTION_THROW` 的发射声只允许保留一条音效链。客户端 `ClientEvents.scheduleUseSounds(...)` 负责为当前投掷安排 `SPLASH_POTION_THROW` 或女巫的 `WITCH_THROW`，并在入队前清除同一施放者尚未播放的冲突发射声，避免把弓箭或重复的药水投掷声混进同一次投掷里；命中后的原版药水破裂 `levelEvent` 仍独立保留。
- 潜影弹：`SHULKER_BULLET` 准备时间固定 12 tick，飞行时间继续复用按距离计算的 projectile flight。服务端对潜影贝专门改用壳体中心朝目标前缘作为发射点，不再复用通用眼睛位置；客户端 `BattleWorldOverlay` 复用原版 `ShulkerBullet` 作为只渲染实体，并在命中点补本地音效与粒子。
- 潜影贝姿态：只有攻击者本体是潜影贝且正在播放 `SHULKER_BULLET` 时，客户端 `ClientEvents.syncVisualShulkerPeekStates(...)` 才会临时把壳体打开；动画结束、战斗结束或实体离开客户端后必须恢复原始 peek 值。玩家或其它非潜影贝单位打出同牌时，只保留潜影弹飞行与命中，不套用潜影贝开壳表现。
- 原版物品使用姿势：弓、弩、三叉戟、喝药水等通过临时主手物品和 `visualUsingItem` 驱动原版使用动画。`ClientEvents.syncVisualUseItemState(...)` 维护 `useItem`、`useItemRemaining` 和 living entity flags，让原版模型按持续使用时间累计姿势。战斗进行中，所有参战者在没有 `visualMainHandOverride` 的帧里都要临时隐藏默认主手物品，避免把进入战斗前的手持物继续显示给其他客户端；只有战斗视觉事件驱动的临时持物可以显示。
- 怪物专属姿势：骷髅弓、溺尸三叉戟、卫道士举斧、恼鬼冲锋、猪灵近战、劫掠兽/疣猪兽头槌、女巫饮药、烈焰人/恶魂蓄火球等都通过 `ClientEvents` 的临时状态同步到原版实体字段，动画结束后必须恢复原状态。
- 监守者专属表现：`WARDEN_MELEE` 使用监守者攻击动画状态和近战命中音效，攻击动画状态由每 tick 的 `visualWardenAttackTick(...)` 在接近完成后启动；`WARDEN_SONIC_BOOM` 在事件消费时播放蓄力音效，若攻击者是监守者则立即强制重开音波动画状态播放本体蓄力动作，到 `animationTicks - 4` 的释放点同一 tick 生成从攻击者到目标的整条 `SONIC_BOOM` 粒子轨迹并播放释放音效；`WARDEN_ROAR` 使用咆哮音效和咆哮动画状态，每次事件消费都会强制重开咆哮动画状态以支持连续使用。攻击者不是监守者时不强求肢体动作，但音波牌仍在释放点保留粒子轨迹和释放音效。
- 受击和击退：造成生命伤害的视觉事件会触发目标 `VisualState.hurtFlash(...)`。客户端通过 `hurtTime` 播放受击闪烁，并用 `knockbackDelta` 在渲染层播放带重力、阻力和回弹 settle 的击退偏移。
- 数字和格挡获得：`BattleVisualEvent` 中的 `blockedDamage`、`healthDamage`、`healedHealth` 和 `gainedBlock` 驱动 `ClientBattleState` 的飘字和 `BlockGainAnimation`，由 `BattleWorldOverlay` 贴在实体 billboard 上渲染。
- 特殊效果：`SELF_DESTRUCT` 通过客户端缩放和白闪表达爆炸蓄势；`UNDYING_REVIVE` 用视觉事件表达复活反馈；`GUARDIAN_BEAM` 用 `GuardianBeamAnimation` 和 overlay 光束渲染；`EVOKER_FANG_LINE`、`EVOKER_FANG_CIRCLE`、`EVOKER_SUMMON_VEX` 由服务端法术动画控制生成时机，并由客户端保持施法姿势。

## 新增或修改动画的检查清单

- 先确认该改动是玩法规则、结算节奏、视觉表现，还是三者都有；玩法规则变化还必须同步 `docs/gameplay.md`。
- 如果新增动画类型，更新 `BattleVisualEvent.AnimationType`，并确认 ordinal 编码兼容当前网络读写逻辑。
- 在 `BattleState.beginBattleAnimation(...)` 或结算发射点中发出正确的 `BattleVisualEvent`，包含合理的 `animationTicks`、起点/命中点、临时手持物、弹射物物品和 `lookTarget`。
- 在 `ClientBattleState` 中决定该类型是否属于 movement、projectile、item-use、spell、charge、self-destruct 或 fireball 类，并补齐 tick、done、默认弹射物和 prepare ticks。
- 在 `ClientEvents` 中同步所有需要的原版实体状态，并确保动画结束后恢复临时主手、使用状态、攻击姿势、蓄力状态和旋转状态。若访问原版受保护字段，需要同步更新 `src/main/resources/META-INF/accesstransformer.cfg` 和 `docs/developer.md` 的访问点说明。
- 在 `BattleWorldOverlay` 中补齐需要的世界渲染，例如弹射物实体、光束、命中特效、billboard 图标或飘字。
- 手动推演至少一遍帧序：输入/出牌、服务端 pending 动画、效果结算点、快照同步、客户端事件消费、视觉状态 tick、渲染插值、动画结束恢复。

## 验证建议

- 编译至少运行 `./gradlew.bat compileJava`，除非本次只改文档。
- 对近战和击退动画，检查实体最终安全位置、锁定位置、客户端视觉 settle 和 walk animation 是否一致。
- 对弓弩、三叉戟、药水、火球等原版姿势，检查动画是否随 tick 持续累计，不应闪烁、不应在进入战斗后保留战斗前的举手姿势。
- 对弹射物，检查准备时间、飞行时间、命中点、朝向、默认 projectile stack 和命中特效是否与服务端结算点对齐。
- 对模态战斗 UI 打开时的视觉反馈，确认基础层飞牌、tooltip、world billboard 和数字不会穿透模态遮罩。
