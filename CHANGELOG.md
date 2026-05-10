# Moon Spire Changelog / 月尖塔更新日志

This file records official release notes for Moon Spire. Future releases should be added above older releases and should keep the same bilingual structure.

本文件用于记录月尖塔的正式发布更新日志。后续版本应追加在旧版本上方，并沿用同一套中英文双语结构。

## Format For Future Releases / 后续版本记录格式

Each release should include:

- Version, release date, Minecraft version, loader, and release type.
- A Chinese changelog first.
- An English changelog with the same categories and matching meaning.
- Categories in this order when applicable: `Release Summary`, `Added`, `Changed`, `Fixed`, `Gameplay`, `Cards And Decks`, `UI And Feedback`, `Developer And Modpack Tools`, `Technical`, `Compatibility`, `Known Notes`, `Future Plans`.
- If a category has no meaningful entry for a release, omit that category for that release instead of leaving an empty section.

每个版本应包含：

- 版本号、发布日期、Minecraft 版本、加载器和发布类型。
- 先写中文更新日志。
- 再写英文更新日志，分类和含义应与中文对应。
- 分类建议按以下顺序使用：`发布摘要`、`新增内容`、`调整内容`、`修复内容`、`玩法系统`、`卡牌与牌组`、`界面与反馈`、`开发者与整合包工具`、`技术实现`、`兼容性`、`已知说明`、`后续计划`。
- 如果某个版本没有对应内容，应省略该分类，不要保留空标题。

---

## 1.0.0 - 2026-05-10

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.228
- License: Apache-2.0
- Release Type: Initial public release

### 中文更新日志

#### 发布摘要

月尖塔 1.0.0 是模组的首个正式发布版本。该版本为 Minecraft 1.21.1 引入一套牌组构筑式回合制卡牌战斗系统，并围绕挑战生物、管理牌堆、打出装备转化卡牌、观察敌人意图和编辑卡牌内容建立完整的基础框架。

本版本的重点不是单个战斗技能，而是一整套可持续扩展的玩法底座：玩家拥有自己的卡牌数据，怪物拥有战斗牌组，战斗会在专门的 UI 和世界头顶反馈中展示，制卡台负责把装备转化为卡牌，开发者中心则用于后续调试、整合包配置和内容扩展。

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

#### Release Summary

Moon Spire 1.0.0 is the first official release of the mod. It introduces a deck-building, turn-based card combat system for Minecraft 1.21.1 and establishes the core framework for challenging creatures, managing card piles, playing equipment-derived cards, reading enemy intents, and editing card content.

This release is not centered on one isolated combat skill. Instead, it provides a full foundation that can keep expanding: players have persistent card data, monsters have battle decks, combat is presented through dedicated screens and overhead world feedback, the Card Forge turns equipment into cards, and the Developer Center supports future debugging, modpack configuration, and content expansion.

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
