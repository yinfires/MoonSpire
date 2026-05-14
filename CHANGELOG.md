# Moon Spire Changelog / 月尖塔更新日志

This file records official release notes for Moon Spire. Future releases should be added above older releases and should keep the same bilingual structure.

本文件用于记录月尖塔的正式发布更新日志。后续版本应追加在旧版本上方，并沿用同一套中英文双语结构。

## Format For Future Releases / 后续版本记录格式

Each release should include:

- Version, release date, Minecraft version, loader, and release type.
- A Chinese changelog first.
- An English changelog with the same categories and matching meaning.
- Categories in this order when applicable: `Added`, `Changed`, `Fixed`, `Gameplay`, `Cards And Decks`, `UI And Feedback`, `Developer And Modpack Tools`, `Technical`, `Compatibility`, `Known Notes`, `Future Plans`.
- If a category has no meaningful entry for a release, omit that category for that release instead of leaving an empty section.

每个版本应包含：

- 版本号、发布日期、Minecraft 版本、加载器和发布类型。
- 先写中文更新日志。
- 再写英文更新日志，分类和含义应与中文对应。
- 分类建议按以下顺序使用：`新增内容`、`调整内容`、`修复内容`、`玩法系统`、`卡牌与牌组`、`界面与反馈`、`开发者与整合包工具`、`技术实现`、`兼容性`、`已知说明`、`后续计划`。
- 如果某个版本没有对应内容，应省略该分类，不要保留空标题。

---

## 1.0.1 - 2026-05-14

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.228
- License: Apache-2.0
- Release Type: Content and stability update

### 中文更新日志

#### 新增内容

- 新增 JEI 联动展示。安装 JEI 时，制卡台转换会显示为 JEI 分类，玩家可以查看装备、弓弩、箭和光灵箭等物品会生成的卡牌。
- 新增弓、弩、箭和光灵箭的专用转换牌，弓弩牌会消耗箭牌并在世界中播放蓄力、装填和飞行表现后结算。
- 新增多种战斗状态与状态图标，包括备箭充足、引信、发光、凋零、潮蚀、麻痹和荆棘。
- 新增更多怪物专属牌组和内置怪物牌，覆盖僵尸、骷髅、蜘蛛、洞穴蜘蛛、苦力怕、溺尸、守卫者和远古守卫者等敌人。

#### 调整内容

- 强化怪物出牌 AI，让怪物能更好地评估伤害、防御、状态施加和自爆等效果。
- 统一普通近战攻击、远程弓弩、骷髅拉弓、三叉戟投掷、激流冲刺和苦力怕自爆等世界战斗动画的表现路径。
- 优化卡组、牌堆、怪物卡组和开发者列表的缓存与可见区域渲染，减少打开列表、滚动和悬停卡牌时的卡顿。
- 调整卡牌效果描述与关键词提示，默认目标不再显示多余“目标”文字，效果描述中的关键词也会高亮并显示对应说明。

#### 修复内容

- 修复卡牌效果搜索栏无法正常使用的问题。
- 修复战斗中视角被中间方块或战斗锁定状态卡住的问题。
- 修复死亡、假死亡和外部死亡保护交互导致的延迟、残留显示或永久假死问题。
- 修复卡牌描述、卡图、世界头顶文字和基础 HUD 内容在模态遮罩上方穿透显示的问题。
- 修复弓弩动画手持物品闪烁、姿态残留、重复挥手和命中结算时机不稳定的问题。
- 修复多人战斗中结束回合、出牌等待、手牌选择确认和旧快照乱序到达可能造成的输入或结算延迟。
- 修复删除或缺失开发者自定义卡后，旧卡牌、旧 id 或无效怪物卡组覆盖在重进后复活的问题。

#### 玩法系统

- 手牌上限固定为 10 张；抽牌超过上限时，额外抽到的牌会直接进入弃牌堆。
- 多怪物战斗中，当前选中或指向哪只怪物，就显示哪只怪物自己的意图和预览数值。
- 右键查看参战单位牌组时，会显示被查看单位当前真实战斗牌库，并包含正在使用或结算中的牌。
- 直接伤害命中会显示更稳定的冲刺、停顿、受击方向和击退表现；效果伤害仍只显示伤害数字、音效和受击反馈，不会触发出牌挥手。

#### 卡牌与牌组

- 弓转换为远程消耗箭造成伤害的卡牌；弩转换为更高伤害、保留并在保留时降费的远程卡牌；箭和光灵箭转换为可被消耗的箭牌。
- 苦力怕牌组加入点燃引信、嘶嘶逼近和火药外壳，并通过引信状态触发无世界破坏的自爆结算。
- 溺尸获得三叉戟投掷、引雷投掷、激流冲刺和鹦鹉螺壳牌组；守卫者和远古守卫者获得潮蚀、麻痹、荆棘和光束相关牌组。
- 怪物默认牌组允许重复牌；无效的非空覆盖被清理后，有默认牌组的怪物会回退到代码内置牌组。

#### 状态与关键词

- 凋零会降低战斗内生命上限，最低降至 1，回合结束时衰减；生命上限恢复后不会自动恢复生命。
- 潮蚀会在目标获得格挡时先抵消格挡；麻痹会削弱目标后续攻击牌的基础伤害；荆棘会在受到攻击伤害时反伤攻击者。
- 备箭充足会在回合开始时生成箭牌；引信会在回合结束时倒计时，归零后触发自爆；烧伤可以引爆带有引信的目标。
- 远程、消耗箭、箭、给予发光和保留时减少耗费接入卡牌描述、关键词提示、怪物意图和战斗结算。

#### 界面与反馈

- 卡组、抽牌堆、弃牌堆、消耗堆和实体牌库查看使用更稳定的连续滚动、可拖动滚动条和可见区域缓存。
- 放大卡牌和关键词提示会更稳定地跟随原卡位置，不再被强行拉回屏幕中心，也不会在原小卡与放大卡之间来回闪烁。
- 开发者中心、卡面应用选择器和卡牌网格的搜索、滚动、确认弹窗、遮罩层和底层输入阻断更加一致。
- 战斗世界头顶反馈会跟随实体真实或视觉移动，凋零后的有效生命上限、状态层数和伤害预览会与实际结算保持一致。

#### 开发者与整合包工具

- 开发者中心怪物页新增初始状态编辑，可为怪物配置凋零、荆棘等战斗开始时自动应用的状态。
- 卡牌效果编辑器支持获得引信、给予凋零、给予潮蚀、给予麻痹和获得荆棘。
- 卡面页新增删除、重置和应用流程；卡面应用选择器支持搜索、批量选择、应用和完成。
- 保存开发者数据后，服务器会把最新卡牌、卡面和怪物数据同步给在线玩家，非管理员只刷新本地缓存而不会自动打开开发者中心。
- 开发者数据加载、保存和删除流程会清理无效卡牌引用、旧怪物卡组覆盖和已删除的自定义卡，避免旧内容复活。

#### 技术实现

- 战斗快照增加序号并让客户端忽略倒序旧快照，减少手牌选择、结束回合和出牌确认后的旧状态回弹。
- 大型战斗牌库内容改为按需请求和按实体、牌堆版本缓存，避免常规快照携带过多牌库数据。
- 世界战斗动画复用更多原版持物、使用物品、骷髅拉弓和激流旋转状态，同时在动画结束、退出战斗或换世界时清理临时状态。
- 新增访问转换配置用于客户端战斗视觉临时读取和写入原版使用物品、激流旋转等字段；这些字段只用于表现，不参与权威伤害或碰撞结算。

#### 兼容性

- 本版本继续面向 Minecraft 1.21.1。
- 本版本继续面向 NeoForge 21.1.228。
- 本版本需要 Java 21。
- JEI 为可选联动；未安装 JEI 时，核心制卡台和卡牌战斗流程不受影响。

### English Changelog

#### Added

- Added JEI integration display. When JEI is installed, Card Forge conversions appear as a JEI category showing the cards produced by equipment, bows, crossbows, arrows, spectral arrows, and other supported items.
- Added dedicated converted cards for bows, crossbows, arrows, and spectral arrows. Bow and crossbow cards consume arrow cards and resolve after draw/loading and projectile travel visuals.
- Added more battle statuses and status icons, including Abundant Arrows, Fuse, Glowing, Wither, Tidal Erosion, Paralysis, and Thorns.
- Added more monster-specific decks and built-in monster cards for enemies such as zombies, skeletons, spiders, cave spiders, creepers, drowned, guardians, and elder guardians.

#### Changed

- Improved monster card AI so monsters can better evaluate damage, defense, status application, and self-destruct effects.
- Unified the presentation path for normal melee attacks, bow/crossbow shots, skeleton bow draw, trident throws, riptide rushes, and creeper-style self-destructs.
- Optimized caching and visible-area rendering for decks, piles, monster decks, and developer lists to reduce stutter while opening lists, scrolling, and hovering cards.
- Adjusted card effect descriptions and keyword tips so default targets no longer show unnecessary target wording, and keywords inside effect text also highlight and show details.

#### Fixed

- Fixed the card-effect search box not working correctly.
- Fixed the battle camera or battle lock getting stuck around blocks in the middle of combat.
- Fixed delayed death, fake-death cleanup, and interactions with outside death protection that could leave stale displays or permanent fake-death state.
- Fixed card descriptions, card art, overhead world text, and base HUD text rendering through modal overlays.
- Fixed bow/crossbow held-item flicker, lingering poses, duplicate swings, and unstable hit-resolution timing.
- Fixed multiplayer end-turn, card-use waiting, hand-selection confirmation, and stale snapshot ordering cases that could delay input or resolution.
- Fixed deleted or missing developer custom cards, old card IDs, and invalid monster-deck overrides coming back after reload.

#### Gameplay

- The hand limit is now fixed at 10 cards. Cards drawn above the limit go directly to the discard pile.
- In multi-monster battles, the selected or aimed monster now determines the displayed intent and preview values.
- Right-clicking a combatant deck now shows that unit's current real battle deck, including cards currently being used or resolved.
- Direct damage now has more stable lunge, hit pause, hurt direction, and knockback feedback; effect damage only shows damage numbers, sound, and hurt feedback, without triggering card-play swings.

#### Cards And Decks

- Bows convert into ranged cards that consume arrows for damage; crossbows convert into higher-damage retained ranged cards that reduce cost while retained; arrows and spectral arrows convert into consumable arrow cards.
- Creeper decks now include Light Fuse, Hissing Approach, and Gunpowder Shell, using Fuse to trigger a self-destruct that does not break world blocks.
- Drowned now have Trident Throw, Channeling Throw, Riptide Rush, and Nautilus Shell decks; guardians and elder guardians now use Tidal Erosion, Paralysis, Thorns, and beam-related decks.
- Default monster decks now allow duplicate cards. When an invalid non-empty override is cleaned up, monsters with built-in defaults fall back to their code-defined decks.

#### Statuses And Keywords

- Wither reduces battle max health down to a minimum of 1 and decays at end of turn; restoring the max health does not automatically heal the unit.
- Tidal Erosion reduces future Block gain, Paralysis lowers the base damage of later attack cards, and Thorns reflects damage back to attackers when attack damage is taken.
- Abundant Arrows creates arrow cards at start of turn. Fuse counts down at end of turn and triggers self-destruct at zero. Burn can detonate a target that has Fuse.
- Ranged, Consume Arrow, Arrow, Glowing, and Retain Reduce Cost now participate in card descriptions, keyword tips, monster intents, and battle resolution.

#### UI And Feedback

- Deck, draw pile, discard pile, exhaust pile, and entity deck views now use more stable smooth scrolling, draggable scrollbars, and visible-range caching.
- Enlarged cards and keyword tips now stay anchored to the original card more reliably, no longer snap toward the screen center, and avoid flickering between the small and enlarged card.
- Developer Center screens, the card-face application selector, and card grids now handle search, scrolling, confirmation modals, overlays, and blocked base input more consistently.
- Overhead battle feedback follows real or visual entity movement, and Wither-adjusted max health, status stacks, and damage previews better match actual resolution.

#### Developer And Modpack Tools

- Added initial effect editing to the Developer Center monster page, allowing monsters to start battles with statuses such as Wither or Thorns.
- Added support in the card-effect editor for Gain Fuse, Apply Wither, Apply Tidal Erosion, Apply Paralysis, and Gain Thorns.
- Added delete, reset, and apply flows to the card-face page; the card-face application selector supports search, batch selection, apply, and done actions.
- Saving developer data now syncs the latest card, card-face, and monster data to online players. Non-admin players refresh local caches without automatically opening the Developer Center.
- Developer data loading, saving, and deletion now clean invalid card references, old monster-deck overrides, and deleted custom cards to prevent stale content from returning.

#### Technical

- Battle snapshots now carry sequence numbers, and clients ignore older out-of-order snapshots to reduce rollback after hand selection, end turn, and card-use confirmations.
- Large battle deck contents are now requested on demand and cached by entity and pile version, reducing the amount of deck data carried in routine snapshots.
- World battle animations reuse more vanilla held-item, item-use, skeleton bow-draw, and riptide spin state, while cleaning temporary visual state after animations, battle exit, logout, or world change.
- Added access transformer entries for temporary client-side battle visuals that read or write vanilla item-use and riptide fields. These fields are presentation-only and do not drive authoritative damage or collision.

#### Compatibility

- This release still targets Minecraft 1.21.1.
- This release still targets NeoForge 21.1.228.
- This release requires Java 21.
- JEI support is optional. Without JEI, the core Card Forge and card-battle flow are unchanged.

## 1.0.0 - 2026-05-10

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.228
- License: Apache-2.0
- Release Type: Initial public release

### 中文更新日志

#### 新增内容

- 新增制卡台方块用于把装备转化为卡牌。
- 新增玩家卡牌数据附件，用于保存玩家拥有的卡牌。
- 新增中英文语言文件，覆盖方块、物品、按键、界面、战斗状态、卡牌效果、关键词、状态效果、命令反馈和开发者工具文本。

#### 玩法系统

- 新增牌组构筑式回合制战斗。玩家可以对可交战目标发起挑战，并进入专门的卡牌战斗流程。
- 新增挑战目标按键。玩家指向可交战目标时，可通过按键发起战斗。
- 新增 10 格挑战与参战范围。发起战斗时，附近 10 格内符合条件的玩家和敌对生物会一起加入战斗。
- 新增玩家方与怪物方回合流程。玩家在自己的回合中出牌，结束回合后怪物按自己的手牌和费用自动行动。
- 新增多人同步战斗基础规则。多个玩家参与同一场战斗时，各自拥有手牌、费用、牌堆和结束回合状态。
- 新增结束回合按钮与快捷键提示。玩家可以通过界面按钮或快捷键Q结束自己的回合。
- 新增战斗取消入口。玩家可按R主动取消当前战斗。
- 新增固定费用基础规则。战斗中每方每回合默认拥有固定费用，用于支付出牌成本。
- 新增抽牌堆、弃牌堆、消耗牌堆和手牌管理。玩家可查看对应牌堆，抽牌堆耗尽后会使用弃牌堆继续循环。
- 新增假死亡与战斗结算基础逻辑。战斗中的死亡会先进入战斗内结算和表现流程，再处理真实死亡与击杀归属。
- 新增速度属性与伤害修正。速度可影响直接伤害表现，并与格挡、守护等防御机制共同参与结算。
- 新增格挡规则。格挡用于吸收普通伤害，并持续到拥有者自己的下个回合开始前。
- 新增敌人意图。玩家可以在自己的回合查看怪物计划使用的卡牌或意图摘要，从而提前判断危险。

#### 卡牌与牌组

- 新增卡牌实例系统，支持卡牌唯一 ID、来源物品、名称键、描述键、费用、攻击、防御、效果列表、来源类型、卡图资源、物品图标和卡面模板。
- 新增卡牌来源类型：模组本体、武器、护甲、工具、怪物、自定义和未知来源。
- 新增可转换装备卡牌。制卡台可以把符合条件的装备转化为玩家拥有的卡牌，并保留来源物品展示。
- 新增基础卡牌效果类型：
  - 伤害。
  - 治疗。
  - 格挡。
  - 流血。
  - 守护。
  - 力量。
  - 扣除力量。
  - 再生。
  - 迅捷。
  - 中毒。
  - 烧伤。
  - 虚弱。
  - 缓慢。
  - 抽牌。
  - 获得费用。
  - 消耗。
  - 固有。
  - 保留。
  - 虚无。
  - 消耗手牌。
  - 丢弃手牌。
- 新增卡牌目标类型：自身、单体敌方、单体友方、全体敌方、全体友方、全体单位、自身以外的所有单位、自身以外的所有友方、随机敌方和随机友方。
- 新增怪物默认牌组系统。默认敌对生物拥有可用于战斗的怪物牌组。
- 新增僵尸类怪物牌组，包括爪击、腐烂防御和猛扑等怪物卡。
- 新增骷髅类怪物牌组，包括骨箭、侧步和瞄准齐射等怪物卡。
- 新增蜘蛛类怪物牌组，包括扑袭、疾行闪避和撕咬等怪物卡。
- 新增通用怪物备用牌组，包括打击、防守和重击等怪物卡，用于没有专属牌组但属于敌对分类的生物。
- 新增卡牌 ID 规范化逻辑，兼容内置怪物卡、物品转化卡和开发者自定义卡。
- 新增被消耗卡牌在战斗结束前移出当前牌组循环的基础机制。

#### 状态与关键词

- 新增关键词：格挡、消耗、费用、固有、保留和虚无。
- 新增流血状态。单位使用攻击牌时会受到流血伤害，回合结束时流血层数会衰减。
- 新增守护状态。每层守护提供直接伤害减免，层数足够高时可阻止直接伤害。
- 新增力量状态。力量提高造成的伤害，负力量会降低造成的伤害。
- 新增再生状态。回合结束时按层数恢复生命并减少层数。
- 新增迅捷状态。迅捷提高速度。
- 新增中毒状态。回合开始时按层数受到伤害并减少层数。
- 新增烧伤状态。回合结束时按层数受到伤害并减少层数。
- 新增虚弱状态。虚弱会降低造成的伤害，并在回合结束时减少层数。
- 新增缓慢状态。缓慢降低速度。
- 新增状态效果图标资源，包括流血、守护、力量、再生、迅捷、中毒、烧伤、虚弱和缓慢。

#### 界面与反馈

- 新增战斗主界面，展示玩家、敌人、手牌、费用、回合状态、结束回合按钮、抽牌堆、弃牌堆和消耗牌堆。
- 新增卡牌渲染系统，使用统一卡面 PNG 绘制费用、名称、类型、描述、卡图和来源物品图标。
- 新增手牌展示与交互。玩家可以查看手牌、悬停放大卡牌，并根据目标和费用尝试出牌。
- 新增牌堆查看面板。抽牌堆、弃牌堆、消耗牌堆和卡组查看界面使用统一的卡牌网格。
- 新增可滚动卡牌网格，支持内容超出时滚轮滚动和滚动条拖动。
- 新增敌方意图展示，帮助玩家判断怪物回合的行动方向。
- 新增世界头顶战斗信息，包括生命、格挡、速度、状态效果、意图和伤害/治疗/格挡反馈。
- 新增战斗视觉事件。出牌、受伤、格挡获得和治疗会在客户端显示对应反馈。
- 新增战斗 UI PNG 资源层，包括按钮、禁用按钮、悬停按钮、费用图标、牌堆图标、面板、滚动条、提示框、血条、格挡填充、生命填充和牌堆数量徽章。
- 新增默认战斗 UI 布局 JSON，集中描述主要战斗界面元素的位置、尺寸和缩放。
- 新增无模糊屏幕基础类，使月尖塔自定义界面避免使用原版菜单模糊背景。
- 新增制卡台界面，玩家可查看背包中可转换装备并生成卡牌。
- 新增卡组界面，玩家可查看和管理自己拥有的卡牌。
- 新增按键绑定：
  - 挑战目标。
  - 打开卡组。
  - 打开开发者中心。

#### 开发者与整合包工具

- 新增开发者中心，用于编辑月尖塔卡牌、卡面、怪物覆盖配置和战斗 UI。
- 新增开发者模式权限限制。开发者工具需要对应权限或配置开启后使用。
- 新增开发者数据目录结构：
  - `moonspire/developer/ui/`
  - `moonspire/developer/card_faces/`
  - `moonspire/developer/cards/`
  - `moonspire/developer/card_art/`
  - `moonspire/developer/monsters/`
- 新增开发者数据保存与拆分文件输出。卡面、卡牌和怪物覆盖数据可写入独立 JSON 文件。
- 新增卡牌编辑功能，支持编辑卡牌 ID、名称、费用、效果、来源类型、卡图、物品图标和卡面。
- 新增卡牌效果编辑器，支持效果类型、数值、次数和目标选择。
- 新增卡面模板编辑功能，支持卡面图片、费用区域、名称区域、卡图区域、类型区域和描述区域调整。
- 新增卡面应用选择器，可将卡面模板应用到指定卡牌。
- 新增怪物覆盖功能，可为生物配置生命、费用、速度和怪物牌组。
- 新增怪物牌组编辑界面，可为指定生物添加和移除卡牌。
- 新增战斗 UI 布局编辑器，可在战斗中调整 UI 元素位置并保存覆盖文件。
- 新增物品搜索、卡牌搜索、效果搜索和目标选择弹窗，提高编辑效率。
- 新增管理员命令：
  - `/moonspire clear_deck`：清空玩家卡组。
  - `/moonspire give_card <card_id> [target]`：向玩家发放指定月尖塔卡牌。

#### 技术实现

- 新增基于 NeoForge 自定义网络载荷的客户端/服务端同步流程。
- 新增战斗快照同步，用于同步战斗阶段、回合、参战单位、手牌、牌堆、敌人意图、待确认手牌选择和视觉事件。
- 新增玩家卡牌数据同步，用于客户端显示当前玩家拥有的卡牌。
- 新增制卡台打开、装备转化、挑战目标、选择目标、使用卡牌、准备卡牌、结束回合、取消战斗、开发者数据保存等网络载荷。
- 新增卡牌和战斗数据的 StreamCodec 编解码流程。
- 新增资源化 UI 绘制工具，统一绘制普通 PNG、九宫格 PNG、按钮、面板、滚动条、提示框和覆盖层。
- 新增基础本地化分类，所有玩家可见文本通过翻译键显示。
- 新增 Apache-2.0 开源协议文件和中文参考说明。

#### 兼容性

- 本版本面向 Minecraft 1.21.1。
- 本版本面向 NeoForge 21.1.228。
- 本版本需要 Java 21 运行环境。
- 现阶段未声明 Fabric、Forge 或其他加载器版本。

#### 已知说明

- 1.0.0 是初始发布版本，后续内容会继续围绕更多生物牌组、更多卡牌、状态效果、遗物、药水、卡牌获取和月尖塔挑战区域扩展。
- 开发者工具主要面向整合包作者、服务器管理者和内容调试场景，普通玩家默认不需要使用。
- 怪物战斗体验会随着后续专属牌组数量增加而继续丰富；没有专属牌组的敌对生物会使用通用备用牌组。

#### 后续计划

- 为更多生物添加专属卡组。
- 增加更多玩家卡牌和状态效果。
- 优化战斗动画、出牌反馈和世界头顶反馈。
- 添加遗物与药水系统，提供更多战斗外策略。
- 添加卡牌升级与获取系统，让卡组构筑更完整。
- 制作更多模组联动卡组与生物挑战。
- 建立真正的月尖塔挑战区域，提供独特生物、卡牌和机制。

### English Changelog

#### Added

- Added the Card Forge block for converting equipment into cards.
- Added a player card-data attachment for storing cards owned by each player.
- Added English and Simplified Chinese language files for blocks, items, keybinds, screens, battle states, card effects, keywords, status effects, command feedback, and developer tools.

#### Gameplay

- Added deck-building turn-based combat. Players can challenge eligible targets and enter a dedicated card-battle flow.
- Added a challenge-target keybind. Players can start combat by aiming at an eligible target and pressing the challenge key.
- Added a 10-block challenge and participation range. When combat starts, eligible nearby players and hostile creatures within 10 blocks can join the same battle.
- Added player-side and monster-side turn flow. Players play cards on their own turn; after they end turn, monsters automatically act according to their hands and energy.
- Added foundational multiplayer synchronized combat rules. Multiple players in the same battle each have their own hand, energy, piles, and end-turn state.
- Added an end-turn button and shortcut hint. Players can end their own turn through the UI button or the Q key.
- Added a battle-cancel entry point. Players can press R to actively cancel the current battle.
- Added the base fixed-energy rule. Each side has a default fixed amount of energy each turn to pay card costs.
- Added draw pile, discard pile, exhaust pile, and hand management. Players can inspect piles, and the discard pile is recycled when the draw pile runs out.
- Added fake-death and battle-resolution logic. Death during battle first goes through combat resolution and presentation before real death and kill attribution are handled.
- Added speed and damage modification rules. Speed can affect direct damage and works alongside Block, Guard, and other defensive mechanics.
- Added Block rules. Block absorbs normal damage and lasts until the owner's next turn starts.
- Added enemy intents. Players can preview monster plans or intent summaries during their turn to judge incoming danger.

#### Cards And Decks

- Added the card instance system, including unique card IDs, source items, name keys, description keys, cost, attack, defense, effect lists, source type, art resources, item icons, and card-face templates.
- Added card source types: mod, weapon, armor, tool, monster, custom, and unknown.
- Added equipment-converted cards. The Card Forge can turn eligible equipment into player-owned cards while preserving source-item presentation.
- Added base card effect types:
  - Damage.
  - Heal.
  - Block.
  - Bleed.
  - Guard.
  - Strength.
  - Lose Strength.
  - Regeneration.
  - Haste.
  - Poison.
  - Burn.
  - Weakness.
  - Slowness.
  - Draw Cards.
  - Gain Energy.
  - Exhaust.
  - Innate.
  - Retain.
  - Ethereal.
  - Exhaust Hand.
  - Discard Hand.
- Added card target types: self, single enemy, single ally, all enemies, all allies, all units, all other units, all other allies, random enemy, and random ally.
- Added default monster deck support. Hostile mobs have battle decks by default.
- Added zombie-family decks with monster cards such as Claw, Rotten Guard, and Lunge.
- Added skeleton-family decks with monster cards such as Bone Shot, Sidestep, and Aimed Volley.
- Added spider-family decks with monster cards such as Pounce, Skitter, and Bite.
- Added a generic fallback monster deck with Strike, Guard, and Heavy Strike for hostile mobs without a dedicated deck.
- Added card ID normalization for built-in monster cards, item-converted cards, and developer custom cards.
- Added the base mechanism for exhausted cards to leave the active deck cycle until battle ends.

#### Statuses And Keywords

- Added keywords: Block, Exhaust, Energy, Innate, Retain, and Ethereal.
- Added Bleed. Units take Bleed damage when using attack cards, and Bleed stacks decay at end of turn.
- Added Guard. Each Guard stack reduces direct damage, and high enough stacks can prevent direct damage.
- Added Strength. Strength increases damage dealt; negative Strength reduces damage dealt.
- Added Regeneration. At end of turn, units heal by stack count and then lose stacks.
- Added Haste. Haste increases Speed.
- Added Poison. At start of turn, units take damage by stack count and then lose stacks.
- Added Burn. At end of turn, units take damage by stack count and then lose stacks.
- Added Weakness. Weakness reduces damage dealt and decays at end of turn.
- Added Slowness. Slowness reduces Speed.
- Added status effect icon resources for Bleed, Guard, Strength, Regeneration, Haste, Poison, Burn, Weakness, and Slowness.

#### UI And Feedback

- Added the main battle screen, showing players, enemies, hand cards, energy, turn state, end-turn button, draw pile, discard pile, and exhaust pile.
- Added card rendering based on a unified card-face PNG for cost, name, type, description, card art, and source item icons.
- Added hand-card display and interaction. Players can inspect hand cards, hover enlarged cards, and attempt plays based on targets and energy.
- Added pile viewer panels. Draw pile, discard pile, exhaust pile, and deck views share a unified card-grid panel.
- Added scrollable card grids with wheel scrolling and draggable scrollbars when content overflows.
- Added enemy intent display to help players read monster actions.
- Added overhead world combat information, including health, Block, Speed, status effects, intents, and damage/heal/block feedback.
- Added battle visual events. Card plays, damage, Block gains, and healing can display client-side feedback.
- Added a PNG-based battle UI resource layer, including buttons, disabled buttons, hovered buttons, energy icons, pile icons, panels, scrollbars, tooltips, health bars, Block fills, health fills, and pile count badges.
- Added a default battle UI layout JSON for centralizing positions, sizes, and scales of major battle elements.
- Added a no-blur screen base class so Moon Spire custom screens avoid the vanilla menu blur background.
- Added the Card Forge screen, where players can inspect convertible equipment and create cards.
- Added the deck screen, where players can view and manage their owned cards.
- Added keybinds:
  - Challenge Target.
  - Open Deck.
  - Open Developer Center.

#### Developer And Modpack Tools

- Added the Developer Center for editing Moon Spire cards, card faces, monster overrides, and battle UI.
- Added developer-mode permission restrictions. Developer tools require the relevant permission or configuration before use.
- Added developer data directories:
  - `moonspire/developer/ui/`
  - `moonspire/developer/card_faces/`
  - `moonspire/developer/cards/`
  - `moonspire/developer/card_art/`
  - `moonspire/developer/monsters/`
- Added developer data saving and split-file output. Card faces, cards, and monster overrides can be written to separate JSON files.
- Added card editing for card ID, name, cost, effects, source type, card art, item icon, and card face.
- Added a card effect editor with effect type, value, count, and target selection.
- Added card-face template editing for face image, cost area, name area, art area, type area, and description area.
- Added a card-face application selector for applying face templates to chosen cards.
- Added monster overrides for configuring creature health, energy, speed, and monster decks.
- Added a monster deck editor for adding and removing cards on a selected creature.
- Added a battle UI layout editor for adjusting UI element positions during battle and saving layout overrides.
- Added item search, card search, effect search, and target picker popups to improve editing speed.
- Added administrator commands:
  - `/moonspire clear_deck`: clears a player's deck.
  - `/moonspire give_card <card_id> [target]`: gives a specific Moon Spire card to a player.

#### Technical

- Added client/server synchronization based on NeoForge custom payloads.
- Added battle snapshot synchronization for battle phase, round, combatants, hand cards, piles, enemy intents, pending hand selections, and visual events.
- Added player card data synchronization for client-side display of the local player's owned cards.
- Added network payloads for opening the Card Forge, converting equipment, challenging targets, selecting targets, using cards, preparing cards, ending turns, canceling battles, and saving developer data.
- Added StreamCodec serialization for card and battle data.
- Added resource-driven UI drawing helpers for regular PNGs, nine-slice PNGs, buttons, panels, scrollbars, tooltips, and overlays.
- Added foundational localization categories so player-facing text is displayed through translation keys.
- Added the Apache-2.0 license file and a Simplified Chinese reference note.

#### Compatibility

- This release targets Minecraft 1.21.1.
- This release targets NeoForge 21.1.228.
- This release requires Java 21.
- Fabric, Forge, and other loader versions are not declared for this release.

#### Known Notes

- 1.0.0 is the initial public release. Future content will continue expanding creature decks, cards, status effects, relics, potions, card acquisition, and the Moon Spire challenge area.
- Developer tools are primarily intended for modpack authors, server administrators, and content-debugging workflows. Regular players do not need to use them by default.
- Monster combat variety will continue to grow as more dedicated creature decks are added. Hostile mobs without a dedicated deck use the generic fallback deck.

#### Future Plans

- Add dedicated decks for more creatures.
- Add more player cards and status effects.
- Improve battle animations, card-play feedback, and overhead world feedback.
- Add relic and potion systems for more strategy outside direct combat.
- Add card upgrade and acquisition systems to complete the deck-building loop.
- Create more cross-mod decks and creature challenges.
- Build the Moon Spire challenge area itself, with unique creatures, cards, and mechanics.
