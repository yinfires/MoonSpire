# Moon Spire 怪物卡组与属性维护说明

本文档是怪物默认卡组、怪物战斗属性、默认初始状态、奖励池、开发者怪物覆盖和运行时回退规则的维护入口。新增、修改或删除怪物卡组、怪物属性、默认初始状态、奖励池、fallback 规则或覆盖逻辑时，必须先阅读本文档，并在同一次变更中同步更新。

## 维护规范

- 怪物卡牌本体由 `MoonSpireCardRegistry` 注册；卡牌 id 必须继续使用英文 canonical 名称派生的稳定 id，不要恢复旧拼音 id 或复用已有 id。
- 怪物默认卡组、默认奖励池、默认战斗生命、默认速度、备箭充足、史莱姆/岩浆怪体型削弱和 fallback 规则都集中维护在 `MonsterDeckProfile`。
- 战斗创建敌方或召唤物时，`BattleState` 先读取 `DeveloperDataManager.monsterOverride(...)` 中的覆盖属性和初始状态，再追加代码内置默认初始状态。
- 开发者中心怪物覆盖由 `DeveloperMonsterDefinition`、`DeveloperDataManager` 和 `DeveloperCenterScreen` 管理；界面、保存/加载清理和运行时回退规则必须保持一致。
- 如果怪物专属卡组触发世界战斗动画，只在本文档保留简短引用；动画类型、时序、渲染钩子、投射物、临时手持物和怪物专属姿态仍以 `docs/battle_animation.md` 为权威。

## 实现链路

- `MoonSpireCardRegistry.builtinMonsterCards()` 注册所有 `builtin_monster_*` 卡牌；物品转换牌如 `item_minecraft_arrow`、`item_minecraft_wind_charge` 也可进入怪物卡组。
- `MonsterDeckProfile.createDeck(...)` 先读取开发者卡组覆盖；覆盖可用则用覆盖列表生成战斗牌库，覆盖显式为空则没有战斗牌库，覆盖失效且该实体有默认卡组时回退默认卡组。
- `MonsterDeckProfile.createDefaultDeck(...)` 按实体类型返回专属默认卡组；未单独列出的敌对怪物使用动态 fallback 卡组。
- `MonsterDeckProfile.defaultDeckCardIds(...)` 给开发者中心显示未覆盖时的默认卡组 id 列表；fallback 在编辑器中显示静态 `FALLBACK_DEFAULT_DECK`。
- `MonsterDeckProfile.rewardPoolCardIds(...)` 读取奖励池覆盖；未覆盖时默认使用开战牌组去重结果。史莱姆和岩浆怪在未覆盖卡组时，奖励池固定使用完整默认卡组去重结果，不受体型削弱影响。
- `BattleState` 在战斗开始时记录 `startingEnemyRewardPools`；分裂子体和战斗中召唤物不会追加到战后奖励池。

## 默认属性与初始状态

- 默认费用使用 `CardBalance.fixedEnergy()`；只有开发者覆盖 `energy > 0` 时才替换。
- 默认生命使用 `MonsterDeckProfile.defaultMaxBattleHealth(...)`：唤魔者固定 60，史莱姆/岩浆怪按大体型 16 点基准和体型削弱计算，其它实体使用原版最大生命且最低为 1。
- 默认速度使用 `MonsterDeckProfile.defaultBaseSpeed(...)`：玩家以外生物按移动速度相对 `CardBalance.NON_PLAYER_BASELINE_MOVEMENT_SPEED` 换算，最低为 1；洞穴蜘蛛固定为 9。
- 开发者覆盖属性只在值有效时生效：`maxHealth > 0`、`energy > 0`、`speed > 0`。无效或等同默认值的字段不保存为覆盖。
- 开发者配置的 `initialEffects` 会先应用；随后 `BattleState.applyDefaultInitialEffects(...)` 追加默认初始状态。
- 普通骷髅、流浪者、沼骸、掠夺者和普通猪灵默认获得 1 层 `ABUNDANT_ARROWS`。
- 史莱姆和岩浆怪按 `Slime#getSize()` 获得默认分裂层数：尺寸 >= 4 为 2 层，尺寸 >= 2 为 1 层，更小为 0 层。若开发者初始状态已经给出分裂层数，则不再覆盖。
- 史莱姆和岩浆怪的中/小体型会使用动态削弱卡牌副本：中型削弱一次，小型削弱两次；卡牌攻击、格挡和效果数值按 50% 向上取整且最低 1。

## 默认卡组索引

以下索引用于维护 `MonsterDeckProfile` 的默认卡组。卡牌显示文本、费用、数值和效果以 `MoonSpireCardRegistry` 中的注册定义为准；本文档只记录实体到卡牌 id 与数量的关系。

- 僵尸、僵尸村民：`builtin_monster_claw` x4，`builtin_monster_rotten_guard` x3，`builtin_monster_lunge` x2，`builtin_monster_undead_power` x1。
- 尸壳：`builtin_monster_claw` x4，`builtin_monster_rotten_guard` x3，`builtin_monster_hungry_lunge` x2，`builtin_monster_undead_power` x1。
- 普通骷髅：`builtin_monster_bow_strike` x3，`builtin_monster_sidestep` x3，`builtin_monster_shoot` x4，`item_minecraft_arrow` x5。
- 流浪者：`builtin_monster_bow_strike` x3，`builtin_monster_sidestep` x3，`builtin_monster_slowing_shot` x4，`item_minecraft_arrow` x5。
- 沼骸：`builtin_monster_bow_strike` x3，`builtin_monster_sidestep` x3，`builtin_monster_poisoned_shot` x4，`item_minecraft_arrow` x5。
- 凋灵骷髅：`builtin_monster_wither_blade` x3，`builtin_monster_charred_guard` x2，`builtin_monster_soul_cleave` x2，`builtin_monster_black_fortress_stance` x1，`builtin_monster_bone_rend` x2。
- 掠夺者：`builtin_monster_drop_the_hanging_blade` x4，`builtin_monster_grazing_cut` x3，`builtin_monster_reload_cover` x3，`item_minecraft_arrow` x5。
- 卫道士：`builtin_monster_axe_chop` x3，`builtin_monster_heavy_axe_blow` x2，`builtin_monster_executioners_blow` x1，`builtin_monster_raised_axe_guard` x2，`builtin_monster_fanatic_might` x2；斧类攻击会触发卫道士斧击世界表现。
- 女巫：`builtin_monster_poison_splash` x2，`builtin_monster_weakness_splash` x1，`builtin_monster_slowness_splash` x1，`builtin_monster_harming_splash` x2，`builtin_monster_healing_draught` x2，`builtin_monster_swiftness_draught` x1，`builtin_monster_healing_splash` x1。
- 普通猪灵：`builtin_monster_piglin_bolt` x4，`builtin_monster_gilded_cut` x3，`builtin_monster_gold_guard` x3，`item_minecraft_arrow` x5。
- 僵尸猪灵：`builtin_monster_vengeful_gold_cut` x3，`builtin_monster_rotten_gold_guard` x2，`builtin_monster_zombified_lunge` x2，`builtin_monster_cursed_gold_stance` x1，`builtin_monster_restless_revenge` x2。
- 猪灵蛮兵：`builtin_monster_brute_chop` x3，`builtin_monster_brute_cleave` x2，`builtin_monster_brute_pressure` x2，`builtin_monster_brute_gold_plate` x2，`builtin_monster_brute_fury` x1。
- 疣猪兽：`builtin_monster_hoglin_gore` x3，`builtin_monster_crimson_headbutt` x2，`builtin_monster_tusks_up` x2，`builtin_monster_crimson_hide` x2，`builtin_monster_herd_fury` x1；专属攻击会触发疣猪兽抬头攻击表现。
- 僵尸疣猪兽：`builtin_monster_zoglin_gore` x3，`builtin_monster_rotten_headbutt` x2，`builtin_monster_maddened_charge` x2，`builtin_monster_dead_hide` x2，`builtin_monster_rotting_trample` x1；专属攻击会触发僵尸疣猪兽抬头攻击表现。
- 蜘蛛：`builtin_monster_pounce` x3，`builtin_monster_skitter` x3，`builtin_monster_bite` x2，`builtin_monster_web` x2。
- 洞穴蜘蛛：`builtin_monster_pounce` x3，`builtin_monster_skitter` x3，`builtin_monster_web` x2，`builtin_monster_venom_fang` x2。
- 蠹虫：`builtin_monster_nipping_bite` x3，`builtin_monster_crackling_mandibles` x2，`builtin_monster_stone_scuttle` x2，`builtin_monster_swarm_alarm` x2，`builtin_monster_infested_call` x1；召唤出的蠹虫使用同一默认卡组或开发者覆盖。
- 末影螨：`builtin_monster_ender_nip` x5，`builtin_monster_rift_skitter` x5；默认奖励池会按这两张牌的去重结果生成，不再使用动态 fallback 卡组。
- 苦力怕：`builtin_monster_light_fuse` x1，`builtin_monster_hissing_advance` x3，`builtin_monster_powder_shell` x3；怪物 AI 会优先使用可用的点燃引信。
- 幻翼：`builtin_monster_raking_dive` x3，`builtin_monster_wingbeat_guard` x3，`builtin_monster_moonlit_glide` x2，`builtin_monster_dragging_talons` x2。
- 恼鬼：`builtin_monster_razor_rush` x3，`builtin_monster_flicker_cut` x2，`builtin_monster_phase_stab` x2，`builtin_monster_evasive_flicker` x2，`builtin_monster_frenzied_dive` x1；攻击牌会触发恼鬼蓄势突刺表现。
- 唤魔者：`builtin_monster_fang_line` x3，`builtin_monster_fang_circle` x2，`builtin_monster_summon_vex` x2，`builtin_monster_totem_of_undying` x1，`builtin_monster_ritual_ward` x2；默认生命固定 60。
- 溺尸：`builtin_monster_trident_throw` x3，`builtin_monster_channeling_throw` x1，`builtin_monster_riptide_rush` x2，`builtin_monster_nautilus_shell` x4；三叉戟牌触发三叉戟/引雷/激流世界表现。
- 守卫者：`builtin_monster_guardian_beam` x3，`builtin_monster_tidal_gaze` x2，`builtin_monster_spiked_carapace` x3，`builtin_monster_deep_sea_reflux` x2。
- 远古守卫者：`builtin_monster_elder_beam` x3，`builtin_monster_elder_tidal_erosion` x2，`builtin_monster_elder_thorn_crown` x3，`builtin_monster_deep_sea_pressure` x2；默认卡组以光束、潮蚀和荆棘为核心。
- 劫掠兽：`builtin_monster_goring_headbutt` x3，`builtin_monster_crushing_charge` x2，`builtin_monster_trampling_pressure` x1，`builtin_monster_thick_hide` x3，`builtin_monster_terrifying_roar` x1；冲撞类攻击会触发劫掠兽头部伸缩表现。
- 史莱姆：`builtin_monster_slime_bump` x3，`builtin_monster_sticky_slap` x2，`builtin_monster_viscous_snare` x2，`builtin_monster_gelatinous_body` x2，`builtin_monster_splattering_pressure` x1；实际战斗卡组会按体型削弱。
- 岩浆怪：`builtin_monster_magma_bump` x3，`builtin_monster_scorching_slap` x2，`builtin_monster_cinder_cling` x2，`builtin_monster_igneous_body` x2，`builtin_monster_eruptive_pressure` x1；实际战斗卡组会按体型削弱。
- 烈焰人：`builtin_monster_blaze_fireball` x3，`builtin_monster_blazing_barrage` x2，`builtin_monster_smoldering_guard` x2，`builtin_monster_heat_haze` x2，`builtin_monster_flame_pressure` x1；火球牌触发小火球世界表现。
- 恶魂：`builtin_monster_ghast_fireball` x3，`builtin_monster_explosive_wail` x2，`builtin_monster_sulfur_drift` x2，`builtin_monster_tearful_ward` x2，`builtin_monster_infernal_shriek` x1；火球牌触发大火球世界表现。
- 末影人：`builtin_monster_ender_stare` x2，`builtin_monster_blink_step` x2，`builtin_monster_void_claw` x3，`builtin_monster_pearl_shift` x1，`builtin_monster_rending_gaze` x2。
- 旋风人：`builtin_monster_wind_charge` x3，`builtin_monster_gale_burst` x2，`builtin_monster_sweeping_gust` x1，`builtin_monster_whirling_guard` x2，`builtin_monster_unsteady_air` x2；风弹类牌触发风弹投射和命中爆发表现。
- 其它未列出的敌对怪物：使用动态 fallback 卡组。实际战斗牌库为 `builtin_monster_strike` x2、`builtin_monster_guard` x2、`builtin_monster_heavy_strike` x1，攻击和格挡数值根据实体攻击力与护甲生成；开发者中心默认列表显示同名静态 fallback id。

## 覆盖、奖励池与回退

- `DeveloperMonsterDefinition` 保存 `maxHealth`、`energy`、`speed`、`initialEffects`、`deckCardIds`、`deckOverride`、`rewardCardIds` 和 `rewardOverride`。
- `deckOverride == false` 表示没有覆盖卡组，敌对怪物回到 `MonsterDeckProfile` 默认卡组，中立和友善生物默认没有可用战斗卡组。
- `deckOverride == true` 且 `deckCardIds` 为空表示显式空卡组；该实体没有战斗卡组，不能被挑战，也不会作为附近敌方加入战斗。
- 非空覆盖卡组允许重复卡牌并保留原始顺序；保存/加载会清理无法解析的卡牌 id，但不会为了奖励池规则对战斗卡组去重。
- 非空覆盖清理后如果变成空卡组，且该实体有默认卡组，会取消这条无效覆盖并回退默认卡组；运行时 `createDeck(...)` 和 `hasBattleDeck(...)` 也保留同样兜底。
- 未自定义奖励池时，奖励池默认等于当前有效开战牌组的去重列表；卡组变化会同步影响默认奖励池。
- `rewardOverride == true` 时使用 `rewardCardIds`，保存和加载会清理无法解析的 id 并去重；清理后为空会取消无效奖励覆盖并回到默认奖励池。
- 删除全局自定义卡时，开发者中心会同步移除怪物卡组和奖励池中的对应引用；保存后服务端也会清理在线玩家收藏和牌组中已无法解析的自定义卡。
- 战斗挑战资格以最终可用战斗卡组为准：敌对怪物没有覆盖时通常可挑战，中立/友善生物必须有开发者配置的非空有效卡组才可挑战；显式空卡组会禁用该实体参战。

## 修改检查清单

- 新增或改名怪物牌：先在 `MoonSpireCardRegistry` 使用英文 canonical id 注册，再更新本文档对应卡组索引；如影响玩家可见描述，还要更新 `docs/gameplay.md`。
- 新增或调整怪物默认卡组：同步更新 `MonsterDeckProfile.createDefaultDeck(...)`、`defaultDeckCardIds(...)` 和本文档索引；如有专属动画，先阅读并按需更新 `docs/battle_animation.md`。
- 修改默认生命、费用、速度、分裂、备箭充足或奖励池：同步更新本文档的默认属性与覆盖规则，并确认开发者中心未覆盖显示仍正确。
- 修改开发者覆盖保存/加载、清理、删除卡牌、奖励池或挑战资格：同步更新本文档和 `docs/developer.md` 的开发者功能摘要。
