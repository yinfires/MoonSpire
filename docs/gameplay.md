# Moon Spire 玩法说明

本文档用于记录 Moon Spire 的玩法规则、玩法变更，以及对应的代码实现方式。

## 记录规范

- 凡是进行了玩法的修改、新增或删除，必须在同一次变更中更新本文档。
- 使用简体中文描述。
- 按玩法类型归类；如果没有合适分类，先新增分类再记录。
- 每条记录需要包含：
  - 玩法描述：玩家会看到或感受到的规则、流程、数值或限制。
  - 代码实现：简要说明由哪些类、数据结构、事件、网络载荷或资源文件实现。
  - 变更记录：记录本次修改、新增或删除的内容。

## 战斗系统

- 玩法描述：分裂是通用战斗状态，层数表示剩余分裂次数。拥有分裂的非玩家生物假死亡后、胜负判定前，会在同一场战斗中分裂出两个同类型、更小、更弱的子体；子体继承父体当前有效战斗牌库，攻击、防御和效果数值按 50% 向上取整且最低 1，费用不提高，最大生命、基础速度和最大能量也按父体战斗值削弱且最低 1。子体获得父体分裂层数减 1；层数为 0 的个体死亡后不再分裂。分裂子体会加入原阵营继续当前战斗，但不会加入战后奖励池，奖励仍只来自战斗开始时的敌人。
  - 代码实现：`BattleEffectType.SPLIT` 定义状态与 `effects/split.png` 图标；`BattleState.tick()` 在 `tickFakeDeaths()` 后和胜负判定前调用 `handlePendingSplits()`，用 `splitCombatant(...)` 创建两个同类型实体并加入 `playerStates` 或 `enemyStates`。生成落点复用 `summonPosition(...)`、`safeSummonGroundPosition(...)`、`stabilizeSummonedEntity(...)` 和 `locks`，并用 `splitOccupiedBoxes(...)` 在既有召唤占位规则上额外避开父体与同批子体，同时把动态实体注册到 `BattleManager` 并通过 `BattleCombatantSnapshot.dynamicCombatant` 让客户端使用既有稳定逻辑。史莱姆使用原版 `Slime#setSize(...)` 降一级，其它生物调整 `Attributes.SCALE` 为父体当前缩放的 70%。`splitChildCards(...)` 复制父体手牌、抽牌堆、弃牌堆和结算中的牌并生成动态削弱副本；`startingEnemyRewardPools` 只在战斗开始时记录，不会追加分裂子体。
  - 变更记录：新增通用“分裂”状态、死亡分裂结算、子体数值削弱、动态参战同步和不加入奖励池规则。
- 玩法描述：怪物默认卡组、默认属性、默认初始状态、默认奖励池和开发者覆盖/回退规则的详细维护说明已迁移到 `docs/monster_decks.md`。玩家在战斗中仍会看到不同敌人使用各自的内置牌组、奖励池和专属表现；本文只保留这些规则对通用战斗流程的影响。
  - 代码实现：怪物牌仍由 `MoonSpireCardRegistry` 注册，默认卡组、默认属性、奖励池和 fallback 由 `MonsterDeckProfile` 提供，战斗初始状态和开发者覆盖由 `BattleState`、`DeveloperDataManager` 与 `DeveloperMonsterDefinition` 应用。
  - 变更记录：新增 `docs/monster_decks.md` 作为怪物卡组与属性维护入口，并把默认卡组清单、默认属性、奖励池和覆盖/回退细节从本文档迁出。
- 玩法描述：`凝视` 是负面战斗状态。目标每次受到攻击伤害时额外受到等同凝视层数的伤害，同一张卡有多次攻击伤害时每次都会触发凝视加伤，凝视在这张卡对该目标的攻击结束后清除。`相位` 是正面战斗状态。目标接下来若干次受到的攻击伤害降低 50%，每次消耗 1 层；相位不影响流血、荆棘、燃烧、中毒等效果伤害。流血以“每次造成攻击伤害时”为触发单位，荆棘以“每次受到攻击伤害时”为触发单位，因此单卡多次攻击伤害会逐次触发。
  - 代码实现：`CardEffectKind` 和 `BattleEffectType` 增加 `GAZE`、`PHASE`；`BattleState.applyPendingCardBatch(...)` 在统一直接攻击入口逐次处理凝视加伤、相位消耗减伤、流血自伤和荆棘反伤，并通过剩余 `PendingCardBatch` / `PendingCardStep` 检查把凝视清除延后到同一卡对该目标的最后一次攻击伤害之后。`CombatantState.applyCardDamage(...)` 接收凝视额外伤害并只对卡牌直接伤害应用相位减伤；`BattleDamageCalculator.phaseReducedDamage(...)` 统一计算相位减伤；`CardRenderHelper`、`BattleScreen`、`BattleWorldOverlay` 和怪物 AI 预览/评分同步识别这两个状态。
  - 变更记录：更新凝视、相位、流血、荆棘的玩家可见描述口径，把单卡多次攻击伤害的触发描述统一为每次受到或造成攻击伤害时。
- 玩法描述：旋风人和玩家使用风弹类远程牌时，会播放原版风弹发射与命中爆发风格的世界表现；玩家把原版风弹物品转换为 `风弹` 牌后打出时，也使用兼容玩家的风弹投射、投掷音效和命中爆发表现。旋风人默认卡组和奖励池维护入口见 `docs/monster_decks.md`。
  - 代码实现：`BattleVisualEvent.AnimationType.WIND_CHARGE`、`BattleState.isWindChargeCard(...)`、`ClientBattleState.ProjectileVisual`、`BattleWorldOverlay.RenderOnlyProjectileEntities` 和 `ClientEvents` 共同把风弹类远程牌表现为原版风弹视觉实体、投掷/发射音效、命中爆发音效与阵风粒子，实际伤害和状态仍由服务端卡牌效果结算。风弹类牌的卡组归属由 `MonsterDeckProfile` 和 `MoonSpireCardRegistry` 维护。
  - 变更记录：将旋风人默认卡组清单迁移到 `docs/monster_decks.md`，保留风弹投射表现规则说明。
- 玩法描述：玩家方非本地友方单位在玩家回合就可以被查看意图；中央意图区卡牌默认不显示，只有 hover 或选中拥有意图的存活单位时才显示对应意图。玩家回合中新召唤出的友方单位也会立即拥有可查看的本轮意图。
  - 代码实现：`BattleState.beginPlayerTurn()` 为玩家方友方预抽本轮自动行动手牌并重建 `playerAllyTurnPlans`；`BattleState.summonEntities(...)` 在玩家回合中新建玩家方召唤物时同步预抽手牌并创建计划，`BattleScreen.currentIntentEntityId()` 只从 hover/selected 的有效意图单位读取中央意图卡。
  - 变更记录：修复玩家回合 hover/selected 友方单位时无法读取意图的问题，并明确中央意图区卡牌保持默认隐藏。
- 玩法描述：战斗期间，参战玩家不能通过碰撞拾取地面掉落物；因此三叉戟牌等战斗表现不会让真实物品进入背包，其它掉落物也会留在世界中，直到战斗结束后再按原版规则拾取。
  - 代码实现：`ServerBattleEvents` 监听 `ItemEntityPickupEvent.Pre`，通过 `BattleManager.isInBattle(...)` 判断玩家是否正在参战，并在参战时返回 `TriState.FALSE` 禁止本次拾取。三叉戟和箭矢类战斗视觉投射物仍由 `BattleState.ProjectileAnimation` 生成，保持 `AbstractArrow.Pickup.DISALLOWED`，并在动画结束或提前结束时清理。
  - 变更记录：新增战斗期间禁止参战玩家拾取世界掉落物的规则，避免三叉戟相关牌或其它战斗过程让真实掉落物进入背包。
- 玩法描述：卡牌战斗改为回合制流程，战斗开始后玩家永远先手，第 1 回合直接进入玩家回合。玩家可在自己的回合出牌，并通过 Q 或结束回合按钮结束回合；随后敌方按手牌和费用自动行动，敌方回合结束后进入下一战斗回合。准备阶段和 Tab 暂停不再参与战斗。玩家可按 R 主动取消当前战斗。
  - 代码实现：`BattleState` 使用 `BattlePhase.PLAYER_TURN`、`MONSTER_TURN`、`ROUND_END` 管理流程，`BattleManager` 通过 `UseCardPayload`、`EndTurnPayload`、`CancelBattlePayload` 和兼容旧载荷转发玩家操作。
  - 变更记录：删除旧的准备/执行倒计时玩法，改为玩家主动结束回合的纯卡牌战斗；新增 R 键取消当前战斗；玩家可见的怪物行动阶段文案改为“敌方回合”。
- 玩法描述：敌方回合中，怪物自动选择手牌时不再固定打出最高分的攻击牌或防御牌，而是先在状态、功能、攻击、防御等可用类别之间按权重随机，再在选中的类别内按评分随机；苦力怕的点燃引信仍作为特殊生物规则，在当前可用时优先使用。负面状态牌仍优先作用于玩家，增益状态牌仍优先作用于自己或其它敌方单位；目标已拥有较多同类状态时，效果牌评分会降低。怪物意图会提前锁定计划，玩家回合显示意图时按怪物下一次行动可用的计划费用预览，不受上一轮怪物剩余费用影响；进入敌方回合后会消费同一套锁定计划，意图区只显示剩余计划牌，不会在行动延迟或效果结算期间重抽另一套随机意图。实际出牌前会重新检查当前手牌、费用和目标。如果计划中的牌费用变得不够、已离开手牌或目标失效，会在执行瞬间跳过该项并用当前手牌重新补选，因此抽牌、生成牌、弃牌、消耗、保留降费、获得能量和后续费用变化都能正确影响怪物后续行动。
  - 代码实现：`BattleState` 为每只敌方缓存 `MonsterTurnPlan`，计划项只保存卡牌 UUID、目标、类别和评分，不保存旧手牌索引或旧费用；`chooseMonsterCard(...)` 对当前可用手牌计算 `MonsterCardCategory`，先应用苦力怕点燃引信优先分支，再执行分类加权随机，`MonsterTurnPlan.cards(...)` 展示意图时按计划费用或当前费用顺序解析 UUID，`MonsterTurnPlan.pollUsable(...)` 在实际行动前按 UUID 重新定位当前手牌并校验当前 `card.cost()`、当前能量、目标和可用性，失效后通过同一随机选择逻辑即时补选。进入敌方回合时不会刷新已有计划，只为缺失计划的敌方补建计划，避免行动开始时重抽另一套意图。
  - 变更记录：怪物 AI 从最高分贪心改为分类加权随机，并保留苦力怕点燃引信优先规则；意图计划兼容手牌增减、费用变化和目标失效，减少怪物长期执着于攻击牌或防御牌的问题；本次修复怪物在下一玩家回合因上一轮剩余费用为 0 而不显示意图，并稳定敌方回合开始时的预览与实际出牌计划。
- 玩法描述：挑战目标范围改为 10 格。玩家指向 10 格内可交战生物时，准星旁会提示按挑战目标键发起挑战；该提示遵循原版 HUD 隐藏状态，按 F1 隐藏 HUD 时同步隐藏。被指向的目标必定加入敌方，不要求它已经敌视玩家。敌对怪物默认拥有怪物卡组；中立和友善生物默认没有卡组，因此默认不能被挑战。开发者为任意可自定义生物配置非空卡组后，该生物才会成为可挑战目标；如果某个生物的最终卡组内容为空，即使它有其它属性覆盖，也不能交战。战斗开始时，10 格内有有效卡组且未参战的其它玩家加入玩家方；10 格内对任一参战玩家有敌意、有非空战斗卡组且未参战的生物加入敌方。
  - 代码实现：`BattleHud` 和 `ClientEvents.CHALLENGE` 共同使用 `ClientEvents.challengeTarget()` 的 10 格客户端射线检测，不依赖原版 `hitResult` 的较短交互距离；`BattleHud.render()` 在 `minecraft.options.hideGui` 为真时跳过准星旁提示绘制；命中后发送 `ChallengeTargetPayload`。客户端提示和服务端 `BattleManager.canChallenge()` 都通过 `MonsterDeckProfile.hasBattleDeck(...)` 判断目标是否有最终可用卡组；`collectPlayers()` 和 `collectEnemies()` 收集参战玩家、被挑战目标和附近敌意且有卡组的生物，并把所有参战实体登记到同一个 `BattleState`。
  - 变更记录：新增多人多怪参战规则，挑战范围从 8 格改为 10 格，并新增准星旁挑战提示；本次将准星旁挑战提示改为遵循 F1/HUD 隐藏状态，将可挑战生物从默认敌对怪物扩展到开发者配置了非空卡组的中立/友善生物，同时禁止所有最终卡组为空的生物交战。
- 玩法描述：开发者可以为生物配置战斗开始时自带的战斗状态。被配置的生物一进入战斗就会带有对应层数的流血、守护、力量、再生、迅捷、中毒、烧伤、凋零、虚弱、缓慢、发光、荆棘等状态；这些状态随后完全按现有状态规则结算，例如中毒会在回合开始时触发，烧伤和再生会在回合结束时触发，凋零会降低战斗内生命上限，发光会同步原版发光视觉。没有配置初始状态的生物仍保持原有开场状态。
  - 代码实现：`DeveloperMonsterDefinition` 新增 `initialEffects`，每个条目由 `DeveloperMonsterInitialEffect` 保存 `BattleEffectType` id 和层数；开发者中心生物页通过状态列表、搜索弹窗和数值输入编辑这些条目。`BattleState` 创建敌方 `CombatantState` 时读取对应生物覆盖配置，并调用 `CombatantState.addEffect(...)` 立即写入状态，因此后续快照、图标、tooltip、回合开始/结束结算和发光同步都复用既有战斗状态链路。
  - 变更记录：新增生物开场战斗状态配置；该配置会作为生物覆盖的一部分保存，并在战斗初始化阶段应用到参战生物。
- 玩法描述：部分远程怪物会在战斗开始时拥有“备箭充足”。备箭充足会在回合开始时，每层生成 1 张转换卡“箭”；如果手牌已经达到 10 张上限，生成的箭会改为进入弃牌堆。哪些实体默认拥有该状态由 `docs/monster_decks.md` 维护。
  - 代码实现：`MonsterDeckProfile.hasAbundantArrowsByDefault(...)` 维护默认名单；`BattleState.applyDefaultInitialEffects(...)` 为对应敌方初始添加 `BattleEffectType.ABUNDANT_ARROWS`，`applyOwnTurnStartEffects(...)` 通过 `MoonSpireCardRegistry.cardInstance("item_minecraft_arrow")` 生成箭，`BattleDeck.addGeneratedToHandOrDiscard(...)` 负责手牌满时放入弃牌堆。
  - 变更记录：保留“备箭充足”的玩家可见生成规则；默认初始状态名单迁移到 `docs/monster_decks.md`。
- 玩法描述：玩家方回合中，所有存活参战玩家可以同时按自己的手牌、费用和目标出牌。玩家按下结束回合后只结束自己的回合，不能继续出牌；只有本地玩家已经结束或正在等待结束回合回包，且仍存在其它存活玩家尚未结束时，界面按钮才显示“等待其他玩家结束回合”。没有其它玩家、其它玩家都已死亡或都已结束时不显示等待文案，敌方回合也不显示等待文案。除本地玩家死亡和真正等待其它玩家外，只有战斗状态本身允许结束回合时才显示“结束第 X 回合”；打开卡组/牌堆遮罩、弃牌/消耗手牌选择界面，或本地玩家刚使用卡牌并等待权威快照/播放出牌动画时，输入会被锁住，但不改变底层“结束第 X 回合”文案和可用底图。敌方行动刚结算完但按钮仍因结算动画、手牌选择确认、结束回合回包、回合结束过渡或本地已结束状态而不可点击时，按钮继续显示“敌方回合”，直到战斗状态本身可结束回合后才显示“结束第 X 回合”；单纯残留的敌方出牌展示卡不再让结束回合按钮显示为敌方回合。所有存活玩家都结束后，才进入敌方回合。已死亡玩家不阻塞回合推进。单体敌方卡牌拖拽时优先使用鼠标当前指向的敌方目标，不再固定回退到第一只敌人。
  - 代码实现：`BattleState` 为每个玩家维护独立 `CombatantState`、手牌、费用、牌堆、目标选择和 `endedTurn`；`BattleCombatantSnapshot` 同步每个玩家的 `endedTurn`，`BattleManager.sync()` 为每名参战玩家发送带本地视角的 `BattleSnapshot`，并由 `BattleState.shouldSyncNow()` 在无视觉事件和无结算时降低同步频率；`BattleScreen.waitingForOtherPlayers()` 只在玩家方回合、本地已结束或正在等待结束回合回包、且 `hasOtherAliveUnendedPlayer()` 确认至少一名其它存活玩家尚未结束时显示等待文案；`BattleScreen.canEndTurn()` 负责真实输入锁，`endTurnButtonLabel()` 和 `endTurnVisualEnabled()` 负责底图/文案状态，允许牌堆遮罩、`snapshot.pendingHandSelection().active()`、手牌选择确认、`awaitingUseCardSnapshot` 和本地玩家出牌飞行动画期间保留玩家回合文案；`ClientBattleState.monsterPlayedCard()` 只影响展示层，不再锁定结束回合按钮；拖牌目标解析通过 `targetEntityUnderMouse()` 优先传入当前指向目标。
  - 变更记录：玩家回合从单玩家行动扩展为多人同步出牌，并新增多人结束回合等待状态；本次修正等待文案的显示条件，避免单人战斗或敌方回合短暂显示“等待其他玩家结束回合”，并修正敌方回合收尾、回合结束过渡或本地已结束等待权威快照时按钮不可用但文案提前显示“结束第 X 回合”的问题。
- 玩法描述：战斗中的死亡都是假死亡。单位战斗生命降到 0 时会立即失去行动和目标资格，并等待死亡动画播放完成，但不会立刻触发真实死亡。世界视觉会先完整播放死亡倒下动画，再播放原版风格白色消散粒子并隐藏该单位，避免假死亡单位长期以红色死亡姿态留在战斗画面中。玩家假死亡后不进入死亡界面，可以继续观战；结束回合按钮显示“你已死亡。”且不可用。整场战斗结束并等待所有假死亡动画完成后，才真正结算死亡；等待时长与客户端死亡动画一致，动画结束后不再额外拖延。由玩家卡牌导致假死亡的怪物会按玩家击杀结算，能触发只有玩家击杀才有的掉落、统计和成就。
  - 代码实现：`CombatantState` 保存 `fakeDead`、`fakeDeathTicks` 和 `creditedPlayerKill`，并通过 `FAKE_DEATH_ANIMATION_TICKS` 统一服务端结算等待与客户端 `ClientBattleState.fakeDeathRenderTicks()` 的死亡动画上限；`ClientBattleState` 为假死亡单位维护死亡动画、消散和隐藏三段视觉状态，死亡动画结束时生成 `ParticleTypes.POOF` 白色粒子并清理该实体的战斗临时视觉状态，`ClientEvents` 在隐藏阶段取消对应实体渲染。`BattleState.tick()` 在一方全灭后等待 `fakeDeathAnimationDone()`，`finish()` 再调用带玩家攻击来源的真实死亡路径。假死亡动画期间 `BattleState.freezeEntity()` 会清除残余速度、跳跃和导航，避免死亡姿态被击退或服务端校正反复打断。旧的 `maxHealth * 2` 高伤害收尾已移除，敌方真实死亡优先使用 `damageSources().playerAttack(killer)` 保留玩家击杀归因。
  - 变更记录：新增高优先级假死亡、观战、延后真实死亡结算和玩家击杀归因规则；本次将服务端假死亡等待从独立魔法数改为与客户端死亡动画一致，减少动画完成后的战斗结束延迟；本次进一步让假死亡单位在死亡动画播完后播放白色消散粒子并从战斗世界画面隐藏，避免红色死亡姿态长期残留。
- 玩法描述：玩家方胜利且战斗完全结束后，每名参战玩家会按战斗开始时的敌方怪物顺序依次获得卡牌奖励机会。每个怪物只弹出一次奖励页；奖励页从该怪物完整且去重的奖励池中随机抽取最多 3 张不重复卡牌。点击卡牌会把该卡加入玩家卡组并进入下一个怪物奖励页；点击“跳过”不会获得卡牌，也会进入下一页。空奖励池不会弹页；玩家方失败、取消战斗或没有胜利结算时不会发放奖励。默认奖励池和开发者覆盖规则见 `docs/monster_decks.md`。
  - 代码实现：`BattleState` 在战斗初始化时记录战斗开始敌方顺序和每个敌方的 `MonsterRewardPool`，后续结算只读取这批开始时敌方。`MonsterDeckProfile.rewardPoolCardIds(...)` 读取完整去重奖励池；`BattleManager.endBattle(...)` 只在 `playerVictory()` 为真时为每名参战玩家创建奖励队列，每页随机洗牌后截取最多 3 张 `CardInstance`，通过 `OpenCardRewardScreenPayload` 打开客户端奖励界面；客户端选择或跳过后发送 `SelectCardRewardPayload`，服务端校验奖励 id、页序号和候选卡 UUID 后才把选择的卡加入 `PlayerCardData` 并同步。
  - 变更记录：保留战斗胜利后的逐怪物奖励流程；默认奖励池和覆盖细节迁移到 `docs/monster_decks.md`。
- 玩法描述：费用暂时固定为每方每回合 3 点，出牌消耗费用，费用不足的牌不能打出。
  - 代码实现：`CardBalance.fixedEnergy()` 固定返回 3，`CombatantState` 在每方回合开始重置费用并在出牌时扣费。
  - 变更记录：移除玩家经验等级和怪物血量对费用上限的影响。
- 玩法描述：玩家和非玩家生物拥有速度属性。每个战斗回合开始时双方基础速度随机加减 2 得到本回合速度。速度只在目标没有格挡时影响攻击牌造成的直接伤害：攻击方速度更高时增伤，防御方速度更高时减伤；目标的格挡会吸收已经完成速度和守护修正后的直接伤害。敌方回合中，高本回合速度的敌方优先行动；本回合速度相同时，按敌方条目从上到下的顺序行动。非玩家默认基础速度和特殊默认速度维护入口见 `docs/monster_decks.md`。
  - 代码实现：`CombatantState` 保存基础速度和本回合速度，`MonsterDeckProfile.defaultBaseSpeed(...)` 提供非玩家默认基础速度；`BattleState.beginRound()` 投掷本回合速度，`CombatantState.applyCardDamage()` 调用 `BattleDamageCalculator.directDamage()` 按目标当前格挡决定是否套用速度乘区，再用目标格挡吸收伤害，剩余部分扣除生命；`BattleState.beginMonsterTurn()` 构建按 `CombatantState.roundSpeed()` 降序排列的 `enemyTurnOrder`，同速时保留 `enemyStates` 原始条目顺序。
  - 变更记录：保留速度影响伤害和敌方行动顺序的玩家可见规则；默认速度换算和特殊默认速度迁移到 `docs/monster_decks.md`。
- 玩法描述：格挡会持续到拥有者自己的下个回合开始前；默认情况下，格挡可以抵挡所有普通伤害，包括攻击牌伤害和流血这类效果伤害。只有特别描述为穿透格挡，或明确写作直接造成生命值伤害的效果，才会绕过格挡直接扣除生命。
  - 代码实现：`BattleState.beginPlayerTurn()` 和 `beginMonsterTurn()` 分别在玩家/怪物各自的回合开始时清理自己的格挡；`CombatantState.applyCardDamage()` 和 `applyEffectDamage()` 统一进入 `applyBlockableDamage()`，先扣减格挡再扣生命；`CombatantState.applyDirectHealthDamage()` 保留给后续明确的直接生命值伤害效果使用。
  - 变更记录：修正怪物格挡在玩家结束回合时过早清空的问题；将普通效果伤害改为默认受格挡吸收，并保留直接生命值伤害的专用入口。
- 玩法描述：守护是正面战斗状态。每层守护提供 10% 攻击伤害减免，2 层显示为获得 20% 减伤；守护最多叠到 9 层，因此最高提供 90% 攻击伤害减免。不同来源的直接伤害增减益使用乘算并四舍五入，例如原伤害 10、速度减伤 50%、守护减伤 50% 时，最终直接伤害为 `round(10 × 0.5 × 0.5) = 3`。守护不减免流血等战斗状态伤害，也不减免明确直接扣除生命值的效果。
  - 代码实现：`CardEffectKind.GUARD` 结算时向目标添加 `BattleEffectType.GUARD`；`CombatantState.addEffect(...)` 和怪物 AI 预览统一把守护限制到 `CombatantState.MAX_GUARD_STACKS = 9`；`BattleDamageCalculator` 统一计算直接伤害的速度乘区和守护乘区，`CombatantState.applyCardDamage()` 使用该结果进入格挡吸收，`applyEffectDamage()` 与 `applyDirectHealthDamage()` 不读取守护。`CardRenderHelper`、`BattleScreen` 和 `BattleWorldOverlay` 复用同一计算规则，让卡牌数值、意图和实际结算保持一致。
  - 变更记录：新增“获得守护”卡牌效果、守护状态图标和关键词说明；怪物意图新增正面增益简写“正”，守护计入正面增益；本次将守护叠层上限统一改为 9 层。
- 玩法描述：角色获得格挡时，会在世界中显示一枚随获得者体型缩放的格挡图标；图标固定显示在获得者身体中间，短暂停留后原地淡出，基本覆盖获得者身体，并同时播放穿铁胸甲音效。该动画使用和战斗状态一样的全局可透视表现，即使被生物模型遮住也能看见。该动画是战斗反馈，不改变格挡数值、持续时间或伤害结算。
  - 代码实现：`BattleState.applyPendingCardBatch()` 在同一批卡牌效果中累计每个目标获得的格挡量，并通过 `BattleVisualEvent.gainedBlock` 同步到客户端；`ClientBattleState` 收到可视事件后创建格挡动画状态，`ClientEvents.playVisualEvents()` 播放 `SoundEvents.ARMOR_EQUIP_IRON`，`BattleWorldOverlay` 使用 `textures/gui/animations/block_gain.png` 以 see-through billboard 方式绘制动画，并根据实体包围盒宽高计算覆盖尺寸。
  - 变更记录：新增获得格挡的世界动画和穿铁胸甲音效反馈，动画资源独立放在战斗动画目录中；动画使用全局可透视渲染并固定在身体中间，避免被生物模型遮住。
- 玩法描述：战斗中双方不能自主移动、原版攻击或原版交互；进入战斗时会清除进入前保留的水平移动、跳跃输入和按键输入，怪物会转向玩家。玩家在半空进入战斗时，战斗的回退点会记录为正下方可站立地面位置，而不是半空坐标；战斗中玩家仍可自然下落。卡牌世界动画可以驱动非自主移动：近战直接伤害会让攻击者短暂冲到目标前并停留在打击点，距离足够近时不移动；“扑袭”会比普通近战更贴近目标，沿最高 1 格的跳跃弧线贴到目标碰撞边缘后攻击。玩家和怪物的近战冲刺、受击击退等战斗位移都会显示平滑位移和行走腿部摆动；带有“远程”的直接伤害默认使用弓或弩使用姿态并射出可穿过方块的箭，没有表现物品的远程牌默认使用弓表现。守卫者光束类远程牌改用原版守卫者光束表现：光束从攻击者眼部射向目标身体中点，按约 80 tick 的原版蓄力节奏持续显示，颜色会从暗蓝绿逐步变亮，并沿光束路径产生气泡粒子；光束释放完毕后才结算伤害、潮蚀、缓慢、荆棘等实际效果，因此伤害数字和状态图标会在光束结束后出现。该规则同时适用于怪物牌和玩家牌，例如玩家通过开发者工具或调试方式使用内置守卫者光束牌时，也会播放同样光束。动画移动无视方块阻挡，但不会恢复玩家输入或怪物 AI 自主移动；双方回合都结束、动画取消、逃离或战斗结束回位时会优先回到无碰撞安全落点，避免实体被锁进方块。卡牌造成血量伤害时，受击者仍会像原版受击一样被轻微击退并向上击飞，击退结束后在新的落点再次锁定，且双方回合都结束时双方回到战斗开始位置。
  - 代码实现：`ServerBattleEvents` 取消战斗中的原版攻击、右键、丢弃和容器行为，`BattleState` 通过碰撞体查找玩家脚下可站立位置作为回退点，并在开战、回合结束与战斗结束时传送回去；`lockBattleEntities()` 每 tick 清除非受击窗口内的水平速度、跳跃状态和移动偏移，但保留 Y 轴速度与重力；`beginBattleAnimation(...)` 对非远程直接伤害创建 `LungeAnimation`，对普通远程直接伤害创建 `ProjectileAnimation`，对 `builtin_monster_guardian_beam`、`builtin_monster_tidal_gaze`、`builtin_monster_elder_beam` 和 `builtin_monster_elder_tidal_erosion` 创建 `GuardianBeamAnimation` 并先发送 `BattleVisualEvent.AnimationType.GUARDIAN_BEAM`；`GuardianBeamAnimation` 不提前调用 `readyToApplyEffects()`，只有 80 tick 持续时间结束后才执行原批次结算。`ClientBattleState` 保存并按客户端 tick 推进 `GuardianBeamAnimation` 视觉状态，按原版步进公式沿攻击者眼位到目标半身高度生成 `ParticleTypes.BUBBLE` 粒子；`BattleWorldOverlay` 使用原版守卫者光束贴图、`RenderType.entityCutoutNoCull(...)`、四组顶点网格、UV 滚动和蓄力颜色公式，从攻击者眼位绘制到目标半身高度；`ClientEvents` 播放 `SoundEvents.GUARDIAN_ATTACK`。当 `BattleState` 存在服务器权威 `BattleAnimation` 时，动画拥有的实体统一用 `noPhysics` 与 `MoverType.SELF` 进行非自主移动，箭实体使用 `setNoPhysics(true)` 和 `setNoGravity(true)` 被引导到目标中心，因此箭和冲击都不会被方块阻挡；`LungeAnimation` 对 `builtin_monster_pounce` 按双方碰撞盒宽度计算贴身打击点，并在冲刺轨迹中叠加 1 格高的跳跃弧线；`ClientBattleState.VisualState` 为近战和击退视觉保存冲刺起点、目标点、击退速度和收尾偏移，`ClientEvents` 在客户端 tick 推进行走动画，并在渲染前对玩家和怪物实体叠加与服务端同形的平滑视觉偏移；`safeReturnPosition(...)`、`restoreSurvivor(...)` 和 `EntityLock` 在重置/结束/取消时恢复原 `noPhysics` 状态并向上扫描无碰撞落点；`applyBattleKnockback()` 在卡牌造成血量伤害时按原版方向调用 `LivingEntity.knockback(...)`，再把初始速度同时交给服务端 `KnockbackState` 推进真实实体位置和客户端 `BattleVisualEvent.knockbackDelta` 平滑表现，落地或速度耗尽后只做必要的最终安全落点校正；`ClientEvents` 在客户端同步清除移动键和非受击窗口内的本地水平预测速度。
  - 变更记录：战斗从实体追逐和自动攻击改为纯卡牌结算；补强进入战斗时的残余水平移动清理、半空自然下落、怪物朝向玩家、服务端同步的受击击退/击飞表现，以及卡牌动画驱动的无碰撞非自主移动；修正半空开战后的回退点和受击击退方向、落地锁定时机；本次让扑袭贴近目标并跳起 1 格高度后攻击，近战命中后不立即后退，并让双方回合结束、动画取消、逃离和战斗结束恢复都使用安全无碰撞落点；本次进一步把玩家近战、击退等战斗位移动画统一到客户端平滑视觉偏移，移除动画过程中的逐 tick 玩家传送，只保留必要的最终权威校正；本次新增守卫者光束动画和光束结束后结算规则，并进一步将光束改为原版 80 tick 蓄力、原版网格/颜色变化和路径气泡粒子表现。
- 玩法描述：战斗外的怪物不会继续攻击或追逐正在卡牌战斗中的玩家或怪物；即使它们已经锁定目标、保留仇恨记忆或处于怒气状态，也会取消对战斗双方的敌意，避免第三方生物干扰当前战斗。
  - 代码实现：`BattleState.tick()` 每 tick 调用 `pacifyOutsideHostiles()`，在战斗双方附近查找不属于当前战斗的 `Mob`，并通过 `pacifyMobAgainstBattle()` 清空目标、攻击记忆、仇恨记录、持久怒气目标，停止导航并取消攻击状态；`BattleManager.handleDamage()` 在战斗外生物已经对战斗中实体造成伤害事件时，也会立即调用 `pacifyOutsideAttacker()` 补清理该攻击者。
  - 变更记录：补强战斗外生物对战斗中双方取消敌意的规则，覆盖已有 target、仇恨记忆、持久怒气和已经发起攻击的情况。
- 玩法描述：战斗开局不会因为怪物原版攻击或进入战斗瞬间的伤害结算而让玩家受击；战斗创建后立即接管双方实体，再开始第一回合。
  - 代码实现：`BattleManager.challenge()` 先把 `BattleState` 登记到玩家和实体索引，再调用 `BattleState.start()`；`BattleState` 在开局短时间内设置双方原版无敌时间、恢复开局生命快照，并清理怪物目标和攻击状态。
  - 变更记录：修复进入战斗后开局被怪物原版攻击打到的问题。
- 玩法描述：使用会作用到非自身目标的牌时，攻击者会先朝向实际目标再使用牌；单体牌朝向该目标，多目标牌尽量朝向目标群的中间处。当前后都有目标导致中间点接近自身或朝向不稳定时，攻击者会改为朝向当前视角变化最小的实际目标，避免突然掉头或看向空处。造成血量伤害时受击者播放受击表现并产生真实的轻微击退和向上击飞，怪物也会被击退，击退方向远离攻击者；完全被格挡时只播放盾牌格挡音效和蓝色伤害数字。
  - 代码实现：`BattleState.queueCard()` 生成待结算批次和 `BattleVisualEvent`，`advancePendingCardSteps()` 在每个真实效果批次进入等待期时创建 `PendingFacing`；`facingPointForBatch()` 从该批次的 `PendingEffect.target()` 收集非自身存活目标，单目标直接取目标位置，多目标先计算水平中心，若中心太近或目标方向抵消则用 `stableFacingTarget()` 选择转动角度最小的实际目标；`tickPendingFacing()` 在 `CARD_EFFECT_START_DELAY_TICKS` 或重复效果间隔内逐 tick 推进头部和身体朝向，`finishPendingFacing()` 在结算或近战/远程动画开始前最终对准。客户端 `ClientEvents.playVisualEvents()` 收到战斗表现事件后播放唯一一次挥手、音效与受击动画，`ClientBattleState` 暂存跳字、受击释放窗口与临时手持物表现。
  - 变更记录：统一玩家和怪物的出牌朝向规则；多目标牌优先朝向真实目标中心，在前后夹击等不稳定布局下改为朝向实际目标，避免急掉头或看向空处；补齐卡牌攻击的受击反馈、格挡反馈和武器临时显示；将击退从客户端本地推力改为服务端原版击退，并让玩家和怪物共用客户端平滑击退偏移，使受击位移同步生效、自然落地且不再依赖逐 tick 玩家传送；修正击退方向和客户端过早清除玩家水平受击速度的问题；移除排队出牌时额外触发的一次提前挥手，避免使用卡牌时出现空挥。

## 卡牌系统

- 玩法描述：所有可获得的 Moon Spire 卡牌都有稳定的字符串卡牌 id。模组内置怪物牌使用 `builtin_monster_*` 格式，物品转换牌使用 `item_<命名空间>_<物品路径>` 格式（路径中的分隔符会转为下划线），开发者自定义牌在统一查询中使用 `custom_<自定义id>` 格式，同时保留直接输入旧自定义 id 的兼容能力。管理员可以通过 `/moonspire give_card <card_id> [target]` 将内置牌、物品转换牌模板或自定义牌加入玩家收藏；通过指令给予物品转换牌不会消耗物品，制卡台转换仍会消耗对应物品。
  - 代码实现：`RegisteredCardDefinition` 集中描述卡牌 id、费用、名称键、描述键、类型、卡图和效果等字段；`MoonSpireCardRegistry` 统一查询内置牌、物品转换牌模板和 `DeveloperDataManager` 读取的开发者自定义牌。`CardInstance` 保存稳定 `cardId` 并兼容旧 NBT；`MoonSpireCommands`、`MonsterDeckProfile` 和 `CardFactory` 都改为通过统一注册入口生成卡牌实例。
  - 变更记录：新增卡牌集中注册与稳定 id 规则，扩展 `/moonspire give_card` 支持内置牌和物品转换牌，并让怪物默认牌组与制卡台转换共享同一套卡牌定义生成流程。
- 玩法描述：内置怪物卡牌使用英文翻译派生的 canonical id，例如 `builtin_monster_claw`、`builtin_monster_undead_power`、`builtin_monster_lunge`、`builtin_monster_hungry_lunge`、`builtin_monster_shoot`、`builtin_monster_poisoned_shot`、`builtin_monster_slowing_shot`、`builtin_monster_drop_the_hanging_blade`、`builtin_monster_grazing_cut`、`builtin_monster_reload_cover`、`builtin_monster_bow_strike`、`builtin_monster_light_fuse`、`builtin_monster_hissing_advance`、`builtin_monster_powder_shell`、`builtin_monster_guardian_beam`、`builtin_monster_tidal_gaze`、`builtin_monster_spiked_carapace`、`builtin_monster_deep_sea_reflux`、`builtin_monster_elder_beam`、`builtin_monster_elder_tidal_erosion`、`builtin_monster_elder_thorn_crown`、`builtin_monster_deep_sea_pressure`、`builtin_monster_nipping_bite`、`builtin_monster_crackling_mandibles`、`builtin_monster_stone_scuttle`、`builtin_monster_swarm_alarm`、`builtin_monster_infested_call`、`builtin_monster_ender_nip` 和 `builtin_monster_rift_skitter`。拼音 id 不再作为有效内置卡保留；后续新增同显示名卡牌时必须加入稳定区分项生成唯一 id，不能复用已有 id。
  - 代码实现：`MoonSpireCardRegistry.builtinMonsterCards()` 注册英文 canonical id 与英文命名的翻译键，`CardInstance.normalizeCardId(...)` 的内置怪物名称映射同步改为新 id；默认卡组由 `MonsterDeckProfile` 引用新 id。`DeveloperCenterScreen.uniqueCardId(...)` 在全新自定义占位卡首次命名时按当前名称派生 id，若与已有卡或内置卡冲突则追加数字后缀，并同步迁移开发者怪物卡组里引用到旧占位 id 的条目。
  - 变更记录：将爪击、亡灵之力、猛扑、射击、弓击改为英文 canonical id，并移除拼音怪物牌 id 兼容路径；新增苦力怕原生卡牌的英文 canonical id；本次新增淬毒射击、迟缓射击和饥饿猛扑的英文 canonical id。
- 玩法描述：`自爆` 是仅用于查看规则的特殊自定义牌，费用为 0，显示“对所有其它单位造成当前自爆伤害，然后死亡”。它不能通过普通卡牌注册入口解析，不能进入玩家牌组、怪物默认卡组、战斗牌堆、掉落池、制卡台、怪物卡组选择列表或普通卡牌列表；玩家未来获得怪物原生牌时也只会获得可实际使用的原生牌，不会获得这张查看牌。
  - 代码实现：`MoonSpireCardRegistry.selfDestructViewCard()` 单独构造 `custom_self_destruct` 查看定义，描述和数值读取 `CardBalance.SELF_DESTRUCT_DAMAGE`；`MoonSpireCardRegistry.card()`、`allCards()`、开发者数据清理、怪物卡组选择器和开发者中心列表过滤共同确保该 id 只在开发者中心自定义筛选下可见。
  - 变更记录：新增只读的 `自爆` 查看牌，并预留玩家获取怪物原生牌时的兼容边界。
- 玩法描述：安装 JEI 时，玩家可以在 JEI 中查看可转换物品的用途，看到该物品通过制卡台会生成的转换卡牌；制卡台会作为该转换分类的关键方块显示。JEI 页面只展示结果和放大预览，不会生成实体卡牌物品，也不改变实际制卡台点击物品后消耗原物品并加入玩家卡牌收藏的规则。
  - 代码实现：`MoonSpireJeiPlugin` 以可选 JEI 插件注册 `CardForgeRecipeCategory`；分类遍历物品注册表并复用 `CardFactory.canConvert()` / `CardFactory.fromItem()` 生成 `CardForgeJeiRecipe`，输入槽绑定原物品，催化剂绑定 `ModItems.CARD_FORGE`。`CardForgeRecipeCategory` 复用 `CardRenderHelper` 绘制转换卡牌和悬停放大预览。
  - 变更记录：新增 JEI 制卡台转换用途联动，允许从物品用途页查看对应转换卡牌，并在分类界面内放大卡牌查看关键词说明。
- 玩法描述：卡牌不再拥有速度字段。卡牌类型由效果决定，能造成血量伤害的牌显示为攻击，其余显示为技能。
  - 代码实现：`CardInstance` 移除 `speed`，保留旧 NBT 兼容读取但忽略旧速度；客户端 `CardRenderHelper` 根据 `hasAttack()` 显示攻击或技能。
  - 变更记录：移除卡牌速度，并更新卡面显示。
- 玩法描述：攻击、获得格挡、施加流血、获得守护、获得荆棘等每条独立卡牌效果在卡面上单独成行并以句号结尾。带数值的效果拥有“次数”，默认为 1；次数为 1 时卡面描述不额外显示，次数大于 1 时在该效果描述末尾显示“X次”，并按单次数值连续结算多次。获得引信的卡面效果只显示获得的引信层数，不在该行追加自爆伤害；自爆伤害只在引信关键词/状态说明和 `自爆` 查看牌里显示。卡牌效果展示与保存时固定把“固有”放在最前、“消耗”放在最后，其它效果保持原配置顺序。铁剑转换出的卡牌额外施加 1 点流血。
  - 代码实现：`CardEffect` 描述额外效果、目标、次数和召唤实体 id，`CardEffectOrder` 统一规范 `CardInstance`、`RegisteredCardDefinition` 和开发者自定义卡的效果顺序；`CardEffectKind.THORNS` 默认作用于自身，并在 `BattleState.applyPendingCardBatch()` 中转换为 `BattleEffectType.THORNS`。`CardEffectKind.SUMMON` 是通用召唤效果，`amount` 表示召唤数量，`count` 表示召唤物存活回合数，`entityTypeId` 表示要创建的实体类型；旧的专用召唤枚举仍归一到同一条结算链路。`MoonSpireCardRegistry` 为铁剑卡添加 `BLEED` 效果；`BattleState.applyCard()` 按 `CardEffect.count()` 分次应用效果，并给重复表现事件加入固定 tick 间隔；`ClientBattleState.consumeVisualEvents()` 延迟播放对应受击、音效和临时手持物表现。`BattleState.summonEntities(...)` 按实体类型创建召唤物、加入施放者同侧、标记 `SUMMONED`、注册动态参战单位并同步快照，恼鬼保留头顶悬浮和唤魔者施法表现，其它生物默认优先生成在施放者左右两侧，按左、右、外侧和前后扩展搜索可站立落点，并避开施放者、已有参战单位和同批召唤物的碰撞盒；地面召唤物生成后会清空动量、同步旧坐标，并在服务端锁位和客户端 `SUMMONED` 待机稳定逻辑中保持固定落点；非恼鬼召唤物待机时关闭重力和物理推挤，避免刚生成后到首次行动前因本地物理预测和锁位修正互相拉扯。语言文件提供每条效果与次数后缀的翻译 key；`CardRenderHelper.descriptionLines()` 将格挡、流血、守护、荆棘、召唤实体名等关键词作为独立组件传入翻译文本，并染成与 `renderKeywordTipsBeside()` 侧边关键词详情标题一致的较深金黄色。
  - 变更记录：新增卡牌效果次数字段与分次动画间隔；新增铁剑流血效果与按效果行渲染的卡面描述；统一关键词显示颜色，并确保放大卡牌在左右侧展示关键词详情；新增守护效果行和开发者中心效果选择项；本次新增可由卡牌获得的荆棘效果，并调整引信获取描述，统一固有/消耗的效果顺序；本次新增通用召唤效果，召唤实体由卡牌数据决定，蠹虫与恼鬼等召唤牌共用同一套结算链路；本次调整非恼鬼召唤物的默认落点，使其从施放者左右侧避让生成，并用实体碰撞盒搜索地面安全位置；本次进一步稳定地面召唤物锁位，减少刚生成时的抖动。
- 玩法描述：战斗中的手牌、放大卡和怪物意图卡会显示当前结算数值，数值低于原始卡牌时数字显示红色，高于原始卡牌时数字显示较深绿色；卡组、抽牌堆和弃牌堆查看界面仍显示卡牌原始数值。未指定目标的玩家攻击牌只按玩家自身状态预览，指定目标后还会把目标速度、格挡和守护等状态计入最终直接伤害；怪物意图会按怪物自身和玩家状态显示最终总伤害。目标当前格挡不会按剩余格挡扣低卡面或意图上的伤害数字，但会让速度乘区不触发。
  - 代码实现：`BattleScreen.cardValues()` 根据当前 `BattleSnapshot`、选中目标、怪物意图和双方 `BattleCombatantSnapshot` 计算战斗中卡牌的 `CardRenderHelper.CardValues`；`CardRenderHelper.previewAttack()` 和 `previewDamageAmount()` 调用 `BattleDamageCalculator.directDamage()`，统一套用速度、格挡触发条件和守护减伤；`CardRenderHelper.descriptionLines()` 只给变化的数值组件上红/绿色样式；`CardGridPanel` 默认使用 `CardValues.original()`，因此牌组系列保持原始数值。
  - 变更记录：新增战斗卡牌最终数值预览和数值颜色变化规则，并修复格挡描述为“获得%s点格挡。”；修正伤害预览不再被目标格挡压低；将增强数值提示绿色调暗，避免过亮；守护和速度/格挡触发条件现在同步影响卡牌与意图数值。

- 玩法描述：怪物意图会预告怪物按当前手牌、费用、生命、格挡和状态计划使用的牌。怪物会按局势给可用牌评分：攻击牌优先击倒玩家，无法击倒时倾向于攻击发光、低生命或低格挡目标；防御牌在低生命、低格挡时收益更高；治疗只在能实际恢复生命时计入收益；增益会倾向给最需要的友方，削弱会倾向给最适合的玩家，已有同类状态较高时会降低收益。苦力怕可用时优先点燃引信；消耗箭牌只有在当前手牌有可消耗箭时才作为有效攻击考虑。怪物一次行动后如果还剩费用和可用手牌，会继续按同一评分规则选择下一张牌；同分时保留手牌靠前者优先。默认怪物卡组、默认属性、专属奖励池和开发者覆盖规则维护入口见 `docs/monster_decks.md`。
  - 代码实现：`BattleState.plannedMonsterCards()` 用虚拟手牌、虚拟费用和 `MonsterAiView` 反复调用 `chooseMonsterCard(...)` 生成意图列表，并模拟已计划牌造成的伤害、格挡、治疗和状态变化；玩家回合展示意图时 `MonsterTurnPlan.cards(...)` 使用计划中记录的费用顺序解析卡牌 UUID，敌方回合实际出牌时 `MonsterTurnPlan.pollUsable(...)` 使用当前手牌索引、当前费用和当前目标重新校验。`chooseMonsterCard(...)` 返回 `MonsterCardChoice`，包含卡牌 UUID、手牌索引、单体目标、类别和评分；`priorityMonsterCard(...)` 会在苦力怕拥有可用 `builtin_monster_light_fuse` 时先返回点燃引信，其它情况再由分类加权随机处理。评分 helper 会统一计算攻击、防御、增益、削弱、手牌处理和消耗箭收益，并把 `CardEffectKind.THORNS` 视为正面状态、把潮蚀、麻痹和饥饿视为负面状态计入评分与意图统计。`tickMonsterTurn()` 消费同一计划出牌并把评分选出的单体目标传入 `queueCard(...)`；多目标和随机目标仍由原有 `targetsForEffect(...)` 结算规则处理。
  - 变更记录：保留局势评分式怪物 AI、消耗箭可用性和连续出牌规则说明；默认怪物卡组清单、实体分流、奖励池和覆盖/回退细节迁移到 `docs/monster_decks.md`。
- 玩法描述：掠夺者、劫掠兽和女巫使用各自专属默认卡组与奖励池；完整卡牌清单维护入口见 `docs/monster_decks.md`。玩家获得这些牌后仍按同一套卡牌效果结算；劫掠兽自己使用头槌、冲锋或践踏类攻击牌时保留原版头部伸缩攻击表现，女巫药水牌继续使用彩色药水卡图、临时手持物和投射物表现。
  - 代码实现：`MoonSpireCardRegistry.builtinMonsterCards()` 注册掠夺者、劫掠兽和女巫内置怪物牌，`MonsterDeckProfile` 负责对应实体的默认卡组分流；劫掠兽攻击视觉由 `BattleVisualEvent.AnimationType.RAVAGER_HEAD_RAM`、`ClientBattleState` 和 `ClientEvents` 驱动，女巫药水通过 `builtinSourceStack(...)` 提供带 `PotionContents` 的源物品。
  - 变更记录：将掠夺者、劫掠兽和女巫默认卡组明细迁移到 `docs/monster_decks.md`，保留玩家可见结算与专属表现摘要。

## 牌组系统

- 玩法描述：唤魔者默认生命和默认卡组维护入口见 `docs/monster_decks.md`。唤魔者尖牙牌按攻击牌结算，吃力量、虚弱、麻痹、荆棘、格挡、速度和守护等攻击相关规则；尖牙实体只作为视觉，不会额外造成原版伤害。不死在死亡后以最大生命值 50% 向下取整复活并减少 1 层；如果复活生命小于 1，例如最大生命值为 1，则复活失败并死亡。召唤出的恼鬼加入召唤者所在阵营，位于召唤者头顶附近，多只横向排列，并带有“召唤物”状态；其它地面召唤生物默认出现在召唤者左右侧的可站立位置，多个召唤物会向外侧和前后扩展，避免与召唤者、已有参战单位和同批召唤物重叠。召唤物回合结束时减少 1 层，层数为 0 时死亡。玩家方召唤物会在真实玩家全部结束行动后、敌方行动前自动行动，友方之间先按本回合速度排序，同速按玩家方条目从上到下排序；真实玩家全灭仍判负，召唤物不能单独维持战斗。所有非本地玩家自己的出牌，包括其它玩家、玩家方友方召唤物和怪物出牌，都统一显示在怪物意图/出牌位置；玩家方友方的意图会和敌方意图一样在条目、世界头顶和顶部小卡中显示。
  - 代码实现：`MonsterDeckProfile` 维护唤魔者默认战斗生命和默认卡组索引；`MoonSpireCardRegistry` 注册唤魔者内置牌。`CardEffectKind.UNDYING`、`EVOKER_FANG_LINE` 和 `EVOKER_FANG_CIRCLE` 接入卡牌描述、AI 评分、攻击预览、目标解析和战斗结算；召唤恼鬼牌使用通用 `CardEffectKind.SUMMON` 并把 `entityTypeId` 写为 `minecraft:vex`。`BattleState.EvokerFangAnimation` 复用原版唤魔者尖牙生成路径并通过 `BattleManager.handleDamage(...)` 拦截 `EvokerFangs` 原版伤害。`BattleEffectType.UNDYING` 和 `SUMMONED` 使用本地 PNG 图标；`CombatantState.applyHealthDamage(...)` 处理不死复活并通过 `BattleVisualEvent.AnimationType.UNDYING_REVIVE` 同步不死图腾粒子和音效。`BattleState.summonEntities(...)` 动态生成召唤实体，登记到 `BattleState` 和 `BattleManager`，用 `SUMMONED` 层数控制存活回合；其中恼鬼保留无 AI、无重力、头顶悬浮和唤魔者召唤施法表现，其它召唤生物禁用 AI，并在待机时关闭重力和物理推挤，通过 `summonPosition(...)`、`safeSummonGroundPosition(...)` 和已占用碰撞盒列表选择左右侧安全落点；`ClientEvents.stabilizeClientBattleSummons(...)` 根据快照中的 `SUMMONED` 状态，在没有战斗位移动画时清空客户端本地移动和走路动画，避免待机召唤物被客户端物理预测拉动；`BattlePhase.PLAYER_ALLY_TURN` 与 `playerAllyTurnPlans` 负责玩家方友方自动行动、意图计划和快照同步，`BattleScreen` 与 `BattleWorldOverlay` 用同一套 intent snapshot 显示友方和敌方意图。
  - 变更记录：将唤魔者默认生命和默认卡组清单迁移到 `docs/monster_decks.md`，保留尖牙、不死、召唤物和友方行动规则说明。

- 玩法描述：战斗使用抽牌堆、手牌和弃牌堆。回合开始弃掉剩余手牌并抽 5 张；打出的牌进入弃牌堆；抽牌堆为空时洗入弃牌堆。
  - 代码实现：`BattleDeck.startTurn()`、`draw()`、`useHand()` 管理抽牌、弃牌和洗牌，并在手牌、抽牌堆、弃牌堆或消耗堆变化时递增本地牌堆版本；`BattleDeck` 构建时复用已经属于本场战斗的 `CardInstance`，避免大卡组入战时逐张二次复制，抽牌时通过随机索引与末尾交换并弹出的方式取得下一张牌，不再在入战或洗入弃牌堆时整堆 `shuffle`；`BattleSnapshot` 只同步牌堆数量、手牌和版本号，完整牌堆内容由 `RequestBattlePilePayload` / `BattlePileContentsPayload` 在玩家打开对应列表时按需同步。
  - 变更记录：替换旧的准备牌池，改为杀戮尖塔式抽牌/弃牌流程；本次将完整牌堆内容从每次战斗快照中移除，并移除战斗牌库构建阶段的重复卡牌复制和整堆预洗牌，避免大卡组在入战和同步时造成卡顿。
- 玩法描述：战斗 HUD 会通过鼠标提示说明抽牌堆和弃牌堆规则。指向抽牌堆时提示每回合开始从这里抽 5 张牌，并说明点击可查看抽牌堆但显示顺序会被打乱；指向弃牌堆时提示抽牌堆空后会把弃牌堆打乱洗回抽牌堆，并说明点击可查看弃牌堆。
  - 代码实现：`BattleScreen.hudTooltipAt()` 使用 `draw_pile` 和 `discard_pile` 布局元素命中范围生成翻译文本提示，`clickPile()` 继续打开对应 `CardGridPanel`，`BattleDeck.draw()` 继续负责抽牌堆为空时洗入弃牌堆。
  - 变更记录：新增抽牌堆和弃牌堆 HUD 提示框，不改变抽牌、弃牌或洗牌结算。
- 玩法描述：带有“消耗”的卡牌只在被打出并完成其它效果后消耗自身，卡牌正文只显示“消耗”，具体规则在关键词提示中说明；仅留在手牌中结束回合时，消耗牌仍按普通手牌弃置，只有“虚无”牌会在回合结束时自动消耗。被消耗的卡牌不会进入弃牌堆，也不会参与后续抽牌堆重洗，它会在本场战斗结束前从战斗牌组循环中移除。玩家和怪物打出的卡牌都会在效果结算期间保持展示，结算结束后普通牌进入弃牌堆，消耗牌播放淡出动画。战斗界面仅在玩家已有被消耗牌时，才会在弃牌堆上方显示“被消耗的牌”入口，数字为居中的粗体紫色，鼠标指向时提示可点击查看本场战斗被消耗的牌，点击后只显示当前玩家自己的被消耗牌。
  - 代码实现：`CardEffectKind.EXHAUST` 作为无目标、无数值、无次数的关键词效果；`BattleDeck` 保存 `exhaustPile`，`BattleState.queueCard()` 跳过消耗效果本身的批次结算，并在其它效果批次结束后把打出的牌按是否带有 `EXHAUST` 移入消耗堆或弃牌堆；`BattleSnapshot` 同步玩家消耗堆数量，完整列表改由按需牌堆内容包同步；`BattleScreen` 使用出牌动画自身的消耗标记决定普通牌飞向弃牌堆或消耗牌淡出，但动画何时离开中央由服务端结算状态和本地等待状态驱动，避免快照到达时跳过中央停留或永久停在中央。
  - 变更记录：新增“消耗”关键词、被消耗牌堆、消耗牌淡出动画和玩家被消耗牌查看入口；本次修复出牌动画释放时机，恢复结算期间中央短暂停留，并为结算结束后的出牌动画添加释放兜底；本次修正回合结束手牌差分动画，避免未打出的消耗牌被表现为虚无消耗。
- 玩法描述：玩家不再手动选择战斗卡组；所有已持有卡牌都会自动进入下一次战斗牌组。卡组界面只用于查看已拥有的卡牌，不能点击选择或取消选择，也没有保存/完成底部按钮。玩家至少需要持有 1 张卡牌才能开始卡牌战斗。
  - 代码实现：`DeckScreen` 只渲染 `ClientCardState.cards().collection()` 的卡牌网格，数量由 `CardGridPanel` 统一显示，不再发送 `SetDeckPayload`；`PlayerCardData.deckCards()` 返回持有卡牌集合，`hasValidDeck()` 仅检查集合非空；`BattleManager.challenge()` 使用 `data.deckCards()` 创建玩家战斗牌库。
  - 变更记录：移除 15-30 张手动组牌限制和卡组保存交互，战斗牌组改为自动使用玩家持有的全部卡牌。
- 玩法描述：玩家按 K 可以打开或关闭自己的卡组界面。非战斗中显示当前持有卡牌；战斗中只有处于最基础战斗界面、没有牌堆弹窗、手牌选择层或布局编辑器等其它界面时，才能按 K 打开本场战斗中自己的当前卡组。战斗中的卡组内容会随抽牌、弃牌、打牌和消耗变化，显示仍在战斗循环中的手牌、抽牌堆和弃牌堆，已消耗牌只在被消耗牌入口中查看。大量卡牌时，战斗牌堆弹窗可以在等待服务端内容包期间先显示遮罩、标题、数量和空网格；内容包到达后必须显示真实卡牌，并通过可视区域渲染、布局缓存和增量预热降低打开与滚动卡顿。
  - 代码实现：`ClientScreens.openDeckScreen()` 让非战斗 `DeckScreen` 支持 K 开关且只在没有其它屏幕时打开，`ClientCardState.version()` 让 `DeckScreen` 只在收藏数据真的更新时刷新 `CardGridPanel`；`CardGridPanel` 通过调用方传入的内容 key 和版本判断是否需要替换列表，稳定帧不再生成整套卡牌签名或扫描旧列表；`BattleScreen.keyPressed()` 在基础战斗界面拦截 K，打开弹窗后按 `battleId + BattlePileSource + localDeckVersion` 请求并缓存当前战斗卡组、抽牌堆、弃牌堆或消耗堆内容，未收到内容包前先显示遮罩、标题、数量和空网格；内容包到达后，只有显示的 `battleId + source + version` 真的变化才重新调用 `CardGridPanel.setCards(...)`，避免打开大牌堆后每帧触发全量卡牌比对。
  - 变更记录：新增战斗中 K 键查看当前战斗卡组，并允许 K 关闭自己卡组界面；本次优化卡组/牌堆大列表打开性能，避免每帧全量比对收藏卡牌和已打开的战斗牌堆，避免渲染路径同步解码并注册本地 PNG 卡图，也避免战斗快照持续同步完整牌堆导致明显卡顿。

- 玩法描述：蠹虫默认卡组维护入口见 `docs/monster_decks.md`。正常蠹虫和召唤出的蠹虫使用同一默认卡组或开发者覆盖；蠹虫召唤时会优先落在召唤者左右侧的地面安全位置，不会生成在身体中心或前方默认点，也会避开其它参战单位。没有额外硬性召唤上限，扩张由费用、抽牌、消耗、召唤物存活回合和战斗结束规则自然限制。玩家通过奖励或指令获得召唤蠹虫牌后打出时，召唤出的蠹虫加入玩家方，并按玩家方友军流程自动行动。
  - 代码实现：`MoonSpireCardRegistry` 注册蠹虫内置怪物牌；`MonsterDeckProfile` 为 `EntityType.SILVERFISH` 维护包含重复牌的专属默认卡组，召唤物创建战斗牌堆时同样调用 `MonsterDeckProfile.createDeck(...)`，因此会读取与正常参战蠹虫一致的默认卡组或开发者覆盖。召唤牌使用通用 `CardEffectKind.SUMMON` 并把 `entityTypeId` 写为 `minecraft:silverfish`；`BattleState.summonEntities(...)` 解析实体 id、创建 `EntityType.SILVERFISH`、加入施放者同侧、应用召唤物存活层数、注册动态参战单位并同步快照，`safeSummonGroundPosition(...)` 使用蠹虫实际碰撞盒搜索脚下有碰撞且自身不进方块的位置，`stabilizeSummonedEntity(...)`、召唤物锁位逻辑和客户端 `SUMMONED` 待机稳定逻辑会清空移动状态，并保持蠹虫在选中的落点上。
  - 变更记录：将蠹虫默认卡组明细迁移到 `docs/monster_decks.md`，保留召唤、落点和玩家方友军规则说明。

## 界面与交互

- 玩法描述：进入战斗后打开纯 UI 卡牌战斗界面，鼠标显示。费用球和抽牌堆位于左下，弃牌堆位于右下，结束回合按钮位于右侧中上区域，手牌集中在屏幕底部，顶部显示玩家和怪物基础条目，条目包含速度、生命和格挡。
  - 代码实现：`BattleScreen` 继承 `NoBlurScreen` 并负责完整战斗 UI，使用独立坐标方法定位费用、牌堆和结束回合按钮；`ClientEvents` 在战斗快照激活时打开该屏幕。
  - 变更记录：将战斗 HUD 调整为接近杀戮尖塔的大小和位置，并保持无模糊背景；抽牌堆和弃牌堆改为独立图标显示，当前数量显示在红色圆形角标内，鼠标悬停时仅图标原位平滑放大，数量角标不缩放。
- 玩法描述：战斗 HUD 会通过鼠标提示说明速度、费用和结束回合。指向条目中的速度数字时显示速度会影响非玩家出牌顺序，并按双方速度差在目标无格挡时增减伤害；指向玩家左下费用时说明当前费用和出牌费用消耗；结束回合按钮显示快捷键括号，鼠标指向时提示按钮会结束你的回合，并说明手牌移入弃牌堆、敌人行动、重新抽 5 张牌并开始下一回合的流程。
  - 代码实现：`BattleScreen.entrySpeedTextRect()` 按当前条目布局和速度文字宽度计算速度命中区域，`hudTooltipAt()` 为速度、费用和结束回合选择对应翻译 key，`renderHudTooltip()` 使用 `MoonSpireUiTextures.drawTooltip()` 绘制多段提示；结束回合快捷键继续由 `BattleScreen.keyPressed()` 和 `ClientEvents` 监听 Q 并发送 `EndTurnPayload`。
  - 变更记录：新增速度、费用和结束回合 HUD 提示框，并在结束回合按钮文字中显示快捷键，不改变战斗流程或数值结算。
- 玩法描述：战斗界面本体不再绘制暗色透明背景，只保留清晰的世界视野和卡牌战斗 UI；卡组查看、牌堆弹窗、布局编辑器等覆盖层仍可以使用暗色透明遮罩作为弹窗底层。
  - 代码实现：`BattleScreen.render()` 不再调用 `renderBackground(...)`；`CardGridPanel`、`MoonSpireBattleLayoutEditor` 这类弹窗/覆盖层仍可独立绘制暗色背景纹理。
  - 变更记录：移除战斗主界面的暗色透明底，只保留弹窗型 UI 的暗色遮罩。
- 玩法描述：战斗界面中按 ESC 会打开原版暂停菜单，而不是关闭或阻塞战斗界面；关闭暂停菜单后仍回到战斗界面。
  - 代码实现：`BattleScreen.keyPressed()` 在 ESC 时打开 `PauseScreen`，`ClientEvents.clientTick()` 在战斗激活时允许 `PauseScreen` 保持在最上层，暂停菜单关闭后再恢复 `BattleScreen`。
  - 变更记录：允许战斗中使用 ESC 打开暂停菜单。
- 玩法描述：战斗中通过暂停菜单退出世界或断开连接时，客户端会离开战斗界面并返回对应的原版退出流程，不会因为本地仍保留旧战斗快照而重新打开战斗界面。
  - 代码实现：`ClientEvents.clientTick()` 只在没有其它界面时自动补回 `BattleScreen`，并在客户端世界或玩家为空时清空 `ClientBattleState`；`ClientPlayerNetworkEvent.LoggingOut` 也会清理本地战斗状态、临时手持物和布局编辑器状态。
  - 变更记录：修复战斗界面中退出游戏后仍停留或回弹到战斗界面的问题。
- 玩法描述：战斗中的“选中”只用于查看怪物意图，只有怪物条目和世界怪物能被点击选中或取消；玩家不能被选中，但可以被鼠标指向并同步显示条目与世界蓝色高亮。攻击牌不再使用已选中怪物作为释放回退，必须拖动箭头实际指向怪物条目或世界中的怪物才能打出；玩家条目被鼠标指向或自我收益牌进入有效出牌区域时，也会显示同款蓝色高亮。卡牌是否需要指向目标只看效果里是否存在单体敌方或单体友方；卡牌进入可使用区域时，会按所有效果里“覆盖数量最高”的目标组来高亮对应单位，当前支持自身、单体敌方、单体友方、全体敌方、全体友方、全体单位和随机敌方。
- 代码实现：`BattleScreen` 将可选中目标与可高亮目标拆分，`selectableTargetUnderMouse()` 只接受怪物，`interactiveTargetUnderMouse()` 和世界射线命中会同时检测玩家与怪物；`ClientBattleState` 维护 hovered 目标集合，`renderEntry()` 和 `BattleWorldOverlay` 用同一份 hover 状态同步条目与世界高亮。`playDraggedCard()`、`playableDraggedCardAt()` 和 `BattleState.usePlayerCard()` 都改为按 `CardTarget.requiresExplicitTarget()` 校验单体敌方/单体友方，其它目标仍可在 `play_area` 布局元素定义的有效出牌区域内直接打出。
  - 变更记录：将选中限定为怪物意图查看，移除选中目标对攻击牌释放的影响；新增玩家条目与世界玩家的 hover/拖牌目标高亮同步；修复攻击牌瞄准目标时整张牌移动到目标位置的问题，并为后续更多目标类型预留了统一目标逻辑。
- 玩法描述：卡牌效果统一为带目标的效果列表，当前兼容造成伤害、获得格挡和施加流血，以及自身、单体敌方、单体友方、全体敌方、全体友方、全体单位、随机敌方目标。卡牌描述会根据效果目标变化：单体敌方保持“造成/给予%s”，单体友方显示为“一名友方”，全体敌方、全体友方、全体单位和随机敌方会写明“对/给予…”的目标范围；给自身施加战斗状态时显示为“获得”。现阶段战斗仍是一名玩家对一只怪物，因此全体敌方、随机敌方和单体敌方会解析到当前敌方，全体友方和单体友方会解析到当前自己，全体单位会同时作用于双方；后续加入多单位战斗时可以继续沿用同一目标枚举。
  - 代码实现：`CardEffect` 统一记录 `CardEffectKind`、数值和 `CardTarget`，`CardInstance` 会把旧攻击/格挡字段兼容迁移为效果列表，并通过稳定卡牌 id 动态读取当前注册定义，使已持有或战斗中的旧卡实例在自定义卡修改后同步使用最新费用、效果、卡图和描述；`BattleState.applyCard()` 统一遍历效果列表并按目标解析后调用伤害、格挡或流血结算；`CardRenderHelper.descriptionLines()` 从同一效果列表和目标渲染卡面文字与关键词，并新增友方目标语言键。
  - 变更记录：将卡牌效果统一到同一个调用入口，保留旧字段兼容，并让自定义卡可直接使用所有已注册效果和目标；新增目标感知卡牌描述和友方目标支持；修复修改自定义卡后旧卡实例仍使用旧效果的问题。
- 玩法描述：世界中战斗双方头上显示血量、格挡、速度、效果和跳字，怪物还显示预计攻击、格挡和负面效果总值；战斗界面怪物条目旁的简要意图也会按怪物本次计划出的所有牌汇总攻击、格挡和流血，带“次数”的效果按每次效果相加显示总和。流血使用独立血滴贴图显示，流血点数数字显示在图标右下角；世界头顶效果会覆盖在生物模型上方，不会被生物身体遮住。格挡伤害显示蓝色数字，血量伤害显示红色数字。
- 代码实现：`BattleWorldOverlay` 在 `RenderLevelStageEvent` 中绘制目标高亮，并在 `RenderLivingEvent.Post` 使用与原版名称牌一致的实体局部矩阵绘制头顶血条、效果、意图与跳字；近战视觉偏移的矩阵在头顶 UI 绘制后才恢复，因此玩家近战视觉位移时头顶条也跟随同一矩阵；怪物扑袭和受击击退由服务端真实实体位置驱动，头顶条、高亮与判定框自然锚定到移动中的实体。头顶 UI 的实体高度偏移使用 `COMBATANT_OVERLAY_Y_OFFSET`，让整组状态显示稳定位于生物头顶上方；`BattleScreen.renderIntentSummary()` 遍历 `monsterIntentCards` 的每条 `CardEffect`，用 `amount * count` 汇总格挡、流血、凋零等效果，并用怪物与玩家速度计算多次伤害总和；状态效果通过 `BattleEffectType.iconTexturePath` 绑定的图标绘制，世界效果图标使用 see-through 渲染类型并将数字绘制到右下角；世界血条复用 `health_bar.png` 九宫格底槽，按底槽暗色内框的 2 像素边距绘制完整填充贴图；`RenderNameTagEvent` 只用于隐藏原版名牌；`BattleVisualEvent` 与 `ClientBattleState` 驱动跳字和音效。
- 变更记录：修复部分场景下世界叠层不显示的问题，保证战斗双方头顶 UI 在战斗中稳定可见，并避免不同渲染通道重复绘制或矩阵空间不一致；流血图标改为独立效果贴图目录并保留右下角点数数字显示；继续修正世界血条底槽与填充对齐；修正怪物简要意图中多次格挡和流血没有按次数累计的问题；上移世界头顶 UI 整体高度，避免第一行战斗状态和血条卡进生物模型或燃烧效果里。
- 玩法描述：战斗基础条目和世界头顶条使用同一套生命/格挡显示逻辑。生命与格挡由贴图式填充显示，生命段和格挡段按当前生命 + 当前格挡的有效耐久比例并排分布，格挡从生命段右侧接续显示，不会越过血条边界或遮住血量文字；基础条目去掉完整底边框，只保留血条、名称、速度和效果。鼠标瞄准或悬停基础条目时显示蓝色边框，只有选中目标时显示黄色边框。
  - 代码实现：`CardRenderHelper.renderCombatantBar()` 和 `BattleWorldOverlay.drawBar()` 共用 `CardRenderHelper.combatantBarSegments()` 计算生命段与格挡段位置，并使用 `health_fill.png`、`block_fill.png` 绘制填充；世界头顶条复用 `health_bar.png` 底槽、按素材暗槽使用 2 像素内框、完整填充 UV 和轻微 Z 分层避免黑块、素材缩进与叠加闪烁；`BattleScreen.renderEntry()` 不再绘制 `combatant_entry.png` 底框，并按选中/悬停状态绘制黄色/蓝色边框。
  - 变更记录：统一基础条目和世界头顶血条表现，改为贴图填充并排分段，修复格挡/生命填充覆盖血条、时而错位和受击后世界血条变黑的问题；统一基础条目瞄准与选中边框颜色。
- 玩法描述：手牌以杀戮尖塔式浅弧形扇排显示，左右两侧轻微下沉并旋转；少量手牌保持平缓，不会形成尖弧或明显高拱，手牌数量较多时才逐步展开弧线并自动压缩间距和缩放。卡牌悬停时原手牌位置不再残留同一张小卡，而是在底部上方克制放大显示；鼠标从小卡移动到放大预览范围内时，仍保持同一张牌的悬停预览，按住放大后的卡牌本体也可以拖出去打牌。卡牌右侧显示格挡、流血等关键词和效果说明；费用不足的放大卡牌费用显示为红色但卡牌本体不变暗，滚轮不再切换手牌选中项。
  - 代码实现：`BattleScreen.handLayout()` 为每张手牌计算中心点、旋转角度和缩放比例，并按手牌数量降低弧线峰值和边缘旋转；`renderHandCard()` 使用矩阵旋转缩放绘制小卡；`hoveredHandIndex` 保持悬停牌，`previewBounds()` 将放大牌固定在底部区域并给命中检测提供缩放后的本体范围；`handPreviewIndexAt()` 允许从放大预览开始拖拽；`BattleScreen.mouseScrolled()` 保留牌堆滚动和空处相机缩放；`CardRenderHelper` 绘制彩色关键词文本、固定宽提示框和最终伤害数值。
  - 变更记录：继续压低手牌弧线、边缘旋转和 hover 放大比例，修复放大预览卡牌不能直接拖出的交互问题，并取消费用不足手牌的暗化遮罩。
- 玩法描述：玩家拖拽需要指向目标的攻击牌并把鼠标指向怪物时，界面优先显示出牌瞄准反馈，不再弹出怪物意图卡牌预览；未拖拽目标牌时，选中或悬停怪物仍可查看怪物意图。
  - 代码实现：`BattleScreen.shouldRenderMonsterIntent()` 在渲染怪物意图前检查当前拖拽牌和鼠标目标；`draggingTargetedCardAtMonster()` 识别攻击牌拖拽到怪物条目或怪物世界目标的情况，并临时关闭意图卡预览。
  - 变更记录：修复拖拽目标牌时怪物意图卡与瞄准反馈同时出现的问题。
- 玩法描述：抽牌堆、弃牌堆、战斗中的玩家/怪物牌组和卡组界面都使用暗色背景的卡牌网格展示；卡牌会按窗口宽度自适应分行排列，鼠标指向任意卡牌时显示放大预览和关键词说明。内容超出时可用滚轮连续滚动，也可直接拖动右侧滚动条。
  - 代码实现：`CardGridPanel` 统一计算卡牌网格列数、可见行、悬停卡牌、放大预览位置和滚动条拖动；`BattleScreen` 的牌堆弹窗和右键牌组查看、`DeckScreen` 的玩家卡牌查看都复用该面板。
  - 变更记录：将旧的竖排小卡列表改为自适应网格展示，新增悬停放大预览和可拖动滚动条，并把打开后的背景改为整屏暗色遮罩。
- 玩法描述：所有卡牌使用统一的图像卡面。费用显示在左上角宝石槽内，卡名、卡牌类型和卡面描述分别在卡面预留区域中水平和垂直居中，并按贴图实际凹槽位置上移对齐；卡牌来源物品图标填充在卡图区域中央，且不会穿透牌堆弹窗等其他界面层。卡牌被选中时不再显示黄色边框。
  - 代码实现：`CardRenderHelper` 使用 `assets/moonspire/textures/gui/cards/card_base.png` 作为卡牌底图，并按底图坐标比例绘制费用、名称、类型、描述和物品图标；卡名避开左上宝石和飘带装饰，描述文本会先按区域宽度换行，再按总行高在描述框中垂直居中；物品图标使用局部矩阵缩放并通过 scissor 限制在卡图区域内，随后清理深度缓冲避免影响后续界面；选中状态不再绘制额外描边。
  - 变更记录：重新标定图像卡面的内容区域并整体上移文本与卡图内容，修复物品图标尺寸和渲染层级问题，并移除选中黄框。
- 玩法描述：战斗中客户端切换到第三人称视角，相机视角以进入战斗时玩家和怪物的中间位置为中心；如果该中心落在方块内部，会改用附近可容纳相机的空位，避免视角被方块碰撞卡住。鼠标拖动可围绕该中心旋转战斗视角，滚轮可拉近或拉远相机，战斗结束后恢复之前的相机模式和相机实体。
  - 代码实现：`ClientBattleState` 保存和恢复相机模式、相机实体并记录相机距离；战斗中创建客户端本地 `CameraAnchor`，首次取得玩家和怪物碰撞盒中心的中点后通过 `resolveCameraCenter()` 检查锚点周围的碰撞空间，优先锁定原中点，必要时向上或向附近水平空位偏移，再作为本场战斗相机中心；锚点使用玩家眼高补偿并同步上一帧位置与旋转，避免原版相机 eye-height 平滑、partial tick 插值或受击位移拖动锚点造成抖动；`Minecraft.setCameraEntity()` 临时让第三人称相机围绕该锚点旋转；`ClientEvents` 使用 `ViewportEvent.ComputeCameraAngles` 和 `CalculateDetachedCameraDistanceEvent` 调整相机角度与距离；`BattleScreen` 在未拖动的点击松开时才处理目标选择，因此从已选中的怪物目标上起手拖动也会旋转视角。
  - 变更记录：目标选择不再占用拖拽旋转；移除顶部区域滚轮切换目标，让滚轮稳定用于相机缩放；相机中心从玩家自身改为进入战斗时玩家和怪物之间的固定中点锚点，并修复进入/退出战斗时相机上下漂移、锚点插值或受击位移导致的抽搐问题；当固定中点位于实体方块内部时，会在附近寻找无碰撞锚点，避免战斗开场视角被压到方块内。

- 玩法描述：所有 Moon Spire 自定义界面的底层面板、按钮、牌堆、提示框、滚动条、战斗条目和战斗头顶条都改为可替换 PNG 资源；卡牌底图继续使用统一卡面 PNG。玩家看到的文字、数值、卡牌和交互流程不变，但界面底图材质可以通过替换同名 PNG 重绘。
  - 代码实现：`MoonSpireUiTextures` 统一绘制普通 PNG 和九宫格 PNG；`BattleScreen`、`CardGridPanel`、`CardRenderHelper`、`DeckScreen`、`CardForgeScreen` 和 `BattleWorldOverlay` 使用 `assets/moonspire/textures/gui/ui/` 下的界面贴图资源。
  - 变更记录：新增界面 PNG 资源层和占位贴图，将原先多处纯色矩形/描边底图迁移为可重绘 PNG。
- 玩法描述：战斗界面的主要 UI 位置、尺寸和缩放由布局数据控制；默认布局来自资源文件，开发者本地覆盖文件可改变战斗条目、牌堆、费用球、结束回合按钮、手牌区域、预览和弹窗等位置。普通玩家默认不会看到调试器。
  - 代码实现：`MoonSpireUiLayout` 读取 `assets/moonspire/ui/battle_layout.json`，并合并 `config/moonspire/battle_layout_override.json`；`BattleScreen` 的渲染、点击检测、目标遮挡和手牌布局共用同一份布局数据。
  - 变更记录：新增战斗 UI 布局 JSON 接口，便于把调试结果直接回填到模组资源中。
- 玩法描述：战斗手牌不再默认选中第一张，玩家只有在手动点选或切换时才会得到当前手牌焦点；被选中的卡牌只通过放大和预览反馈，不再额外显示高亮边框。怪物意图里的小卡在未悬停时直接按正常顺序绘制，避免预览后残留原位置卡图。
  - 代码实现：`ClientBattleState` 将默认手牌选中值改为 `-1`，`BattleScreen` 仅在选中手牌时放大而不画选中框，`CardRenderHelper` 去掉选中描边并为非放大小卡增加不使用裁剪的绘制路径，`BattleScreen` 的怪物意图先绘制普通卡再绘制悬停预览。
  - 变更记录：修复战斗中高亮层级覆盖、默认首张自动选中、小卡图错位和怪物意图残留问题。
- 玩法描述：卡组查看、弃牌堆查看和其它卡牌网格弹窗不再使用棕色边框背景，只保留整屏暗色透明遮罩和滚动条，界面会更接近箱子打开后的暗色透明效果；制卡台也使用同类无模糊暗色透明背景。
  - 代码实现：`CardGridPanel` 移除了九宫格棕色面板背景，只保留 `overlay.png`、滚动条和卡牌网格；`CardForgeScreen` 继续通过 `NoBlurScreen` 维持无 blur 的暗色透明背景。
  - 变更记录：去掉卡组系列 UI 的棕色边框，统一为暗色透明遮罩。
- 玩法描述：战斗 UI 布局调试器中，当前选中的 UI 元素会最后绘制黄色边框，避免被其它 UI 轮廓覆盖；同时可以用 Tab / Shift+Tab 在元素之间切换选择，方便调试被遮挡的条目。
  - 代码实现：`MoonSpireBattleLayoutEditor.render()` 将选中元素延后绘制，`keyPressed()` 增加 Tab 循环选择，选中轮廓改为黄色。
  - 变更记录：修复布局调试器中部分 UI 被覆盖后无法选中的问题。

## 效果系统

- 玩法描述：格挡会阻挡伤害。玩家格挡持续到下回合开始前；怪物格挡持续到下一个玩家回合结束，因此玩家看到怪物上回合获得的格挡后，仍有一个完整玩家回合可以处理它。
  - 代码实现：`CombatantState.defense` 保存格挡值；`BattleState.beginRound()` 只清空玩家格挡，`BattleState.endPlayerTurn()` 在玩家回合结束时清空怪物格挡。
  - 变更记录：怪物格挡不再在敌方回合结束后立刻消失，改为维持到下一个玩家回合结束。
- 玩法描述：流血会让拥有者在使用攻击牌时受到等同于当前流血点数的伤害，回合结束时变为 1/3(向下取整)点，降为 0 时移除；查看已经附着在生物身上的流血效果时，说明会显示当前实际伤害数值，例如 1 点流血显示为受到 1 点伤害。
  - 代码实现：`BattleEffectType.BLEED`、`BattleEffectSnapshot` 和 `CombatantState` 管理流血层数，`BattleState.triggerBleedForAttack()` 在攻击牌结算前触发流血伤害；`CardRenderHelper.renderEffectTip()` 使用 `BattleEffectType.activeDescriptionKey()` 和当前效果数值渲染生物效果提示。
  - 变更记录：新增流血效果；补充生物身上效果说明按当前实际数值显示。
- 玩法描述：潮蚀是负面战斗状态。拥有者获得格挡时，会先减少本次即将获得的格挡，最多减少等同于潮蚀层数的数值；每实际减少 1 点格挡，潮蚀减少 1 层。如果本次没有获得格挡，则不会额外消耗潮蚀。回合结束时，潮蚀额外减少 1 层。
  - 代码实现：`BattleEffectType.TIDAL_EROSION` 注册潮蚀状态并绑定图标与翻译；`CombatantState.addDefense(...)` 统一作为格挡入口，先按当前潮蚀层数扣减本次格挡并同步消耗层数，然后返回实际获得的格挡值。`BattleState` 记录格挡获得动画时使用返回值，避免被潮蚀抵消后仍播放错误格挡数字；怪物 AI 的 `MonsterAiView.addDefense(...)` 使用同一扣减规则。`CombatantState.decayEndOfTurnEffects()` 在回合结束时让潮蚀减少 1 层。
  - 变更记录：新增潮蚀战斗状态；不新增施加潮蚀的卡牌，开发者可通过初始状态等既有状态入口让单位带入战斗。
- 玩法描述：麻痹是负面战斗状态。拥有者接下来打出的攻击牌造成的基础伤害减少 5 点，影响的攻击牌数量等同于麻痹层数；每打出 1 张攻击牌，麻痹减少 1 层。麻痹直接降低攻击牌基础伤害，最低降至 0，然后再进入力量、虚弱、速度、守护和格挡等后续结算；没有打出攻击牌时不会消耗层数。
  - 代码实现：`BattleEffectType.PARALYSIS` 注册麻痹状态并绑定图标与翻译；`BattleState.queueCard()` 在攻击牌进入结算队列时读取麻痹层数，使用 `CardBalance.PARALYSIS_ATTACK_DAMAGE_REDUCTION` 扣减本张攻击牌的基础伤害，并在打出攻击牌时调用 `CombatantState.reduceEffect(...)` 消耗 1 层。普通伤害和消耗箭牌的基础伤害都走同一调整入口；怪物 AI 评分、怪物意图汇总、卡牌预览和世界头顶意图预览也按麻痹后的基础伤害计算。
  - 变更记录：新增麻痹战斗状态；不新增施加麻痹的卡牌，开发者可通过初始状态等既有状态入口让单位带入战斗。
- 玩法描述：荆棘会在拥有者受到攻击伤害结算时，对攻击者造成等同于荆棘层数的伤害；即使本次攻击伤害被格挡完全吸收，也会触发荆棘。荆棘反伤属于效果伤害，会先被攻击者的格挡吸收，不受速度、力量、虚弱、守护或发光等攻击伤害修正影响，也不会再次触发荆棘。卡牌现在可以直接获得荆棘，例如守卫者的棘刺甲壳获得 5 点格挡和 2 层荆棘，远古守卫者的远古棘冠获得 8 点格挡和 3 层荆棘。
  - 代码实现：`BattleEffectType.THORNS` 保存荆棘层数并通过 `thorns.png` 显示图标；`CardEffectKind.THORNS` 默认目标为自身，`BattleState.applyPendingCardBatch()` 会把卡牌荆棘效果写入目标的 `BattleEffectType.THORNS`，并只在非 `effectDamage` 的 `CardEffectKind.DAMAGE` 结算后调用 `applyThornsDamage(...)`，使用荆棘拥有者作为反伤来源、通过 `CombatantState.applyEffectDamage(...)` 对攻击者结算反伤，并为反伤单独发送 `BattleVisualEvent`。`BattleState` 的怪物 AI 评分和 `BattleScreen`、`BattleWorldOverlay` 的意图统计会把荆棘视为正面状态。如果荆棘拥有者是玩家，反伤击杀会通过 `playerKillCredit(...)` 保留玩家击杀归因。
  - 变更记录：新增“荆棘”战斗状态；开发者可通过生物初始状态配置让怪物开场携带荆棘，状态说明和图标会随战斗快照同步显示；本次新增卡牌荆棘效果、卡面描述和开发者效果选择项，并在守卫者/远古守卫者卡组中使用。
- 玩法描述：鼠标指向条目中的效果图标时，会在附近显示效果名称和描述详情。
  - 代码实现：`BattleScreen.renderEffects()` 检测效果图标悬停并调用 `CardRenderHelper.renderEffectTip()` 绘制固定宽详情框。
  - 变更记录：补齐流血等效果的悬停详情展示。

## 界面与交互补充

- 玩法描述：战斗 UI 的悬停、拖拽、回合提示、抽牌与弃牌动画都改成客户端插值动画。玩家看到的 hover 上抬、回位、技能牌跟随鼠标、攻击牌从手牌区拉出瞄准箭头并可直接拖到怪物基础条目或世界怪物上打出、拖拽进入战场中部偏下的可打出区域时显示可打出背景高亮并触发一次更强短促高亮、同一次拖拽中离开可打出区域后再次进入会再次触发强高亮、出牌以普通手牌小卡大小用 0.2 秒滑到战场中央并停留 0.2 秒且保持高亮、随后直接缩小为手牌小卡四分之一大小并用 0.5 秒飞向弃牌堆且取消高亮、其它进入弃牌堆的卡牌也以手牌小卡四分之一大小用 0.5 秒飞入弃牌堆、回合切换 banner、可出牌高亮和结束回合脉冲提示，都是本地根据战斗快照差分推断出来的表现层。
- 代码实现：`BattleScreen` 维护 `HandCardAnimation`、`FlyingCardAnimation`、`DragState`、`turnBannerTicks` 和 `uiTicks`，通过 `ClientBattleState.snapshotVersion()` 只在战斗快照变化时按手牌 UUID、phase 和玩家费用做差分；手牌与飞牌动画在渲染帧中按真实帧间隔推进，避免只按 20 TPS 游戏 tick 更新；`playable()` 与服务端出牌条件对齐，要求费用足够且卡牌有攻击、格挡或效果；`targetEntityUnderMouse()` 同时识别怪物基础条目和世界实体，`playAreaContains()` 读取 `play_area` 布局元素定义战场中部偏下的出牌区域，并用手牌区顶部限制下边界以避免手牌误触；`playableDraggedCardAt()` 将实际指向怪物作为攻击牌释放判定；`playDraggedCard()` 对攻击牌使用当前 `HandCardAnimation` 中心作为出牌飞行动画起点，避免瞄准态从鼠标指针处突然飞出；`FlyingCardAnimation` 固定用 4 tick 表示 0.2 秒的出牌移动和中央停留、用 10 tick 表示 0.5 秒弃牌飞行，并用 `SMALL_CARD_WIDTH / CARD_WIDTH` 把中间展示缩放到普通手牌小卡大小；`DragState` 记录上一帧是否处在可打出位置，并在每次从不可打出变为可打出时启动短促高亮脉冲；`FlyingCardAnimation.showPlayableGlow()` 只在到中间与停留阶段返回 true，进入弃牌阶段后关闭高亮；`renderTurnBanner()`、`drawAimLine()`、`renderHandCard()`、`renderHoveredHandCard()`、`renderDraggedDetailedCard()` 和 `renderDraggedCard()` 负责具体绘制。
- 变更记录：放大预览、普通手牌、手牌移动回位和拖拽牌统一使用费用足够且满足出牌条件的基础可打出高亮；攻击牌不再因为选中怪物而像技能牌一样在普通出牌区域释放，必须用瞄准箭头实际指向怪物条目或世界怪物；普通出牌区域改为 `play_area` 布局元素，可在战斗 UI 调试界面调整位置和大小。减少手牌目标重复同步，改用快照版本和帧内缓存避免同一帧重复计算手牌布局、hover 和目标命中，手牌、抽牌飞入和弃牌飞出改用真实渲染帧间隔推进；攻击牌出牌飞行动画从手牌当前位置开始；出牌中央展示固定为普通手牌小卡大小，0.2 秒滑到中央、停留 0.2 秒后直接缩为手牌小卡四分之一大小并用 0.5 秒飞入弃牌堆；拖拽可打出位置和打出后中间展示补齐可打出高亮，弃牌阶段取消高亮，同一次拖拽中每次从不可打出位置重新进入可打出位置都会触发一次更强高亮；继续保持可用状态只显示高亮而不把不可用手牌变暗。
- 玩法描述：卡牌费用显示为类似尖塔费用风格的单层描边数字，字号以正常手牌小卡为基准，并随放大预览、卡组和牌堆中的卡牌实际显示比例同步缩放；战斗界面左下当前费用使用专用费用图标，当前费用大于 0 时显示金色图标，当前费用为 0 时显示灰色图标，图标中央以更大的字体显示当前/最大费用比值，不再显示“费用”文字。
  - 代码实现：`CardRenderHelper.drawCardCostNumber()` 读取当前 `GuiGraphics` pose 的外层缩放，并按当前卡牌屏幕宽度与正常手牌小卡宽度的比例计算费用字号；描边只绘制四向单层深色边和主体白字，不再叠加额外阴影；`BattleScreen.renderEnergy()` 调用 `CardRenderHelper.renderEnergyCostDisplay()` 按当前费用选择 `cost_available.png` 或 `cost_empty.png`，并用 2 倍屏幕字号叠加 `screen.moonspire.energy_ratio`。
  - 变更记录：替换战斗当前费用显示方式，并统一卡牌费用数字风格。
 玩法描述：结束回合按钮使用单层描边文字，不再带阴影；玩家回合可正常结束时显示白色描边文字，鼠标指向时文字变红；敌方回合显示白色描边文字；玩家回合没有可出的牌时按钮保持蓝色高亮并持续显示黄色描边文字，且按钮底图仍会随鼠标悬停切换。
  - 代码实现：`BattleScreen.renderEndTurnButton()` 按 `hasPlayableCard()`、鼠标命中和战斗阶段选择按钮贴图与文字颜色，并复用 `CardRenderHelper.drawOutlinedScreenText()` 绘制单层描边文字；没有可出牌时只额外绘制青色脉冲外发光，同时仍保留按钮本体的 hover 命中判定。
  - 变更记录：调整结束回合按钮的字体阴影、颜色和无牌可出提示状态。
- 玩法描述：默认战斗布局已同步用户运行时覆盖结果，玩家条目、怪物条目、怪物意图、费用球、抽/弃牌堆、结束回合按钮、手牌区域和牌堆覆盖层都采用新的默认位置和尺寸。
  - 代码实现：`assets/moonspire/ui/battle_layout.json` 直接回填 `moonspire/developer/ui/battle_layout_override.json` 的调试布局值，`MoonSpireBattleLayoutEditor` 继续使用同一套 schema 读写资源和覆盖文件，并兼容旧的 `config/moonspire/battle_layout_override.json`。
  - 变更记录：把运行时调试布局同步进默认资源布局，减少发布版和调试版的界面偏差。

## 方块与物品

- 玩法描述：制卡台在世界中和物品栏里的外观使用锻造台式模型与贴图布局，但贴图资源以制卡台自己的 `card_forge_*` 名称保存在模组资源中，方便后续单独改图。
  - 代码实现：`assets/moonspire/models/block/card_forge.json` 使用 `minecraft:block/cube` 并分别引用 `textures/block/card_forge_top.png`、`card_forge_front.png`、`card_forge_side.png` 和 `card_forge_bottom.png`；物品模型 `assets/moonspire/models/item/card_forge.json` 继续继承方块模型。
  - 变更记录：将制卡台从单一紫水晶方块贴图改为复制自原版锻造台的多面模型与本地贴图资源。
- 玩法描述：月尖塔拥有独立的创造模式物品栏，图标使用制卡台；制卡台会出现在该物品栏中，后续通过月尖塔物品 DeferredRegister 注册的物品也会自动加入该物品栏。
  - 代码实现：`ModCreativeModeTabs` 注册 `moonspire:moon_spire` 创造模式物品栏并在 `BuildCreativeModeTabContentsEvent` 中读取 `ModItems.creativeTabItems()`；`ModItems.creativeTabItems()` 直接返回 `ITEMS.getEntries()`，因此 `ModItems` 后续注册的物品会自动展示。
  - 变更记录：新增月尖塔创造模式物品栏，并将制卡台加入该物品栏。
- 玩法描述：制卡台的方块属性与原版工作台一致；玩家可以使用有序配方合成制卡台，配方为上排 2 张纸、下排 2 个木板。
  - 代码实现：`ModBlocks.CARD_FORGE` 使用 `BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)` 复制原版工作台属性；`data/moonspire/recipe/card_forge.json` 使用 `minecraft:crafting_shaped`，形状为 `PP`/`WW`，其中 `P` 为纸、`W` 为木板标签。
  - 变更记录：制卡台属性改为工作台属性，并将旧配方替换为 2 纸 2 木板的有序合成。
- 玩法描述：制卡台和工作台一样不需要特定挖掘等级，玩家空手也能破坏并获得掉落物，使用斧类工具可以更快挖掘。
  - 代码实现：制卡台继续使用工作台复制属性，不带 `requiresCorrectToolForDrops`；`data/minecraft/tags/block/mineable/axe.json` 将 `moonspire:card_forge` 加入斧挖掘标签；`data/moonspire/loot_table/blocks/card_forge.json` 让方块破坏后掉落 `moonspire:card_forge`。
  - 变更记录：新增制卡台掉落表和斧挖掘标签，使其挖掘需求与工作台一致。

## 指令与调试

- 玩法描述：开发者模式默认关闭；“切换战斗 UI 布局编辑器”按键默认绑定为 F8 并可在按键设置中调整。开启 `config/moonspire/client.json` 中的 `developerMode` 后，开发者可在战斗中使用该按键选择 UI 元素并调整位置、缩放、宽度和高度，保存结果供模组开发使用。未开启开发者模式或不在战斗中时，按键只显示提示，不会打开调试器。
  - 代码实现：`MoonSpireClientConfig` 读取客户端开发者模式配置；`ClientEvents` 始终注册 `key.moonspire.ui_debug`，按下时重新加载客户端配置并检查开发者模式与战斗界面状态；`BattleScreen.keyPressed()` 也处理同一按键，保证战斗界面获得焦点时仍可切换；`MoonSpireBattleLayoutEditor` 在战斗界面中显示调试选框并保存布局覆盖 JSON。
  - 变更记录：新增客户端战斗 UI 布局调试功能，调试数据保存为可迁移的布局 JSON；修复战斗界面中已设置按键但无法打开布局编辑器的问题。
- 玩法描述：拥有 2 级管理员权限的玩家可以使用 `/moonspire clear_deck [target]` 清空目标玩家持有的所有 Moon Spire 卡牌；清空后该玩家的下一次战斗牌组也会为空，直到重新获得卡牌。
  - 代码实现：`MoonSpireCommands.clearDeck()` 清空 `PlayerCardData.collection()` 中的持有卡牌，并同步清理遗留的 `deck()` 选择列表，然后写回玩家附件并通过 `BattleManager.syncCardData()` 同步客户端。
  - 变更记录：修复清除卡组指令只清理旧版手动组牌列表、没有移除实际持有卡牌的问题。

## 数据与同步

- 玩法描述：客户端显示的卡牌、速度、生命、格挡、效果、牌堆、目标、怪物预计行动和表现事件都来自服务端同步。怪物预计行动同步为怪物按当前手牌、计划费用、生命和格挡策略即将实际尝试打出的牌序列，实际行动前仍会按当前费用、手牌 UUID 和目标存活状态重新校验。
- 代码实现：`BattleSnapshot` 包含双方 `BattleCombatantSnapshot`、本地牌堆数量、本地手牌、本地牌堆版本、首个怪物手牌、首个怪物预计单牌、首个怪物预计牌列表、额外怪物预计牌列表和 `BattleVisualEvent` 列表；首个怪物不再重复写入额外怪物意图列表，本地玩家和首个怪物的手牌也不再重复写入实体手牌列表。抽牌堆、弃牌堆、消耗堆和战斗中 K 卡组的完整内容由 `RequestBattlePilePayload` / `BattlePileContentsPayload` 按需同步；`BattleState` 在玩家输入、抽牌/弃牌/消耗、阶段切换、手牌选择、视觉事件和死亡/结束判定等权威状态变化时标记同步脏状态，`shouldSyncNow()` 只在脏状态、视觉事件、结束流程或低频心跳时发送完整快照，`BattleManager.sync()` 发送后清理视觉事件和脏标记；`BattleState.plannedMonsterCards()` 在玩家回合负责刷新计划，在敌方回合只读取并显示同一套锁定计划的剩余可执行牌，`BattleSnapshotPayload` 负责同步权威战斗状态。
  - 变更记录：修复怪物实际出牌和预计显示不一致的问题；本次减少战斗快照中的重复怪物意图和重复实体手牌同步，降低开战首次同步与战斗中快照序列化卡顿。

## 本次战斗目标与卡牌效果更新

- 玩法描述：战斗中的“选中”只用于查看怪物意图，只有怪物能被点击选中或取消，玩家不能被选中。选中怪物不会再让攻击牌直接在普通出牌区域释放；攻击牌必须拖动箭头指向怪物条目或世界中的怪物才能打出。攻击牌拖到目标上时，卡牌仍停留在手牌瞄准位置，只更新箭头、目标高亮和可出牌反馈。玩家仍可被鼠标指向或被自我目标牌指示，此时玩家条目和世界中的玩家都会显示蓝色高亮；拖动格挡等自我收益牌进入有效出牌区域时，也会用同款蓝色高亮显示玩家。单体友方不是自己，当前 1v1 里不能拿自己当单体友方目标；全体友方和自身以外的所有单位是范围目标，不要求拖箭头。
  - 代码实现：`BattleScreen` 将可选中目标与可高亮目标拆分，`selectableTargetUnderMouse()` 只接受怪物，`interactiveTargetUnderMouse()` 和世界射线命中会同时检测玩家与怪物；`renderEntry()` 使用 `ClientBattleState.hoveredEntityId()` 同步条目高亮，黄色只表示已选中的怪物。`playDraggedCard()` 和 `playableDraggedCardAt()` 不再把 `selectedTargetId` 当作攻击牌目标回退，攻击牌只在实际指向怪物时发送 `UseCardPayload`；`renderDraggedCard()` 的攻击牌分支始终使用手牌瞄准卡渲染，不在命中怪物时切换为跟随鼠标的大卡。
  - 变更记录：将选中限定为怪物意图查看，移除选中目标对攻击牌释放的影响；新增玩家条目与世界玩家的 hover/拖牌目标高亮同步；修复攻击牌瞄准怪物时整张牌移动到目标位置的问题。
- 玩法描述：怪物每实际使用一张牌时，怪物意图区域中央会显示一张比意图小卡略大的正在使用牌，无论玩家是否选中了该怪物。连续出牌时新牌会替换旧牌；最后一张牌使用完成后约 1 秒消失。该展示牌位于最顶层，不会被怪物意图小卡、手牌、拖拽牌或飞行动画遮住。
  - 代码实现：`BattleVisualEvent` 携带可选 `playedCard`，`BattleState.applyCard()` 在每张牌结算时发送对应表现事件，非伤害牌也会发送无伤害展示事件；`ClientBattleState` 保存当前怪物展示牌和剩余显示时间，收到新怪物用牌事件立即替换；`BattleScreen.renderMonsterPlayedCard()` 在基础战斗 UI、手牌、飞牌和拖拽牌之后绘制展示牌，使用基于意图小卡的小幅放大比例，并按渲染帧真实时间推进 1 秒保留时间。
  - 变更记录：新增怪物正在使用的卡牌置顶展示，并保证使用完成后自动移除；将展示尺寸从正常大卡缩小为略大于意图小卡。
- 玩法描述：卡牌效果统一为带目标的效果列表，当前兼容造成伤害、获得格挡和施加流血，以及自身、单体敌方、单体友方、全体敌方、全体友方、全体单位、自身以外的所有单位、随机敌方目标。卡牌描述会根据效果目标变化：单体敌方和自身默认描述保持简洁，其它目标会写明“对全体敌方”“给予随机敌方”等目标范围；给自身施加战斗状态时显示为“获得”。现阶段战斗仍是一名玩家对一只怪物，因此全体敌方、随机敌方、单体敌方和自身以外的所有单位会解析到当前敌方，全体友方不会把自己算作友方，单体友方当前没有其它友方单位时没有可用目标，全体单位会同时作用于双方；后续加入多单位战斗时可以继续沿用同一目标枚举。
  - 代码实现：`CardEffect` 统一记录 `CardEffectKind`、数值和 `CardTarget`，`CardInstance` 会把旧攻击/格挡字段兼容迁移为效果列表，并通过稳定卡牌 id 动态读取当前注册定义，使已持有或战斗中的旧卡实例在自定义卡修改后同步使用最新费用、效果、卡图和描述；`BattleState.applyCard()` 统一遍历效果列表并按目标解析后调用伤害、格挡或流血结算；`CardRenderHelper.descriptionLines()` 从同一效果列表和目标渲染卡面文字与关键词。
  - 变更记录：将卡牌效果统一到同一个调用入口，保留旧字段兼容，并让自定义卡可直接使用所有已注册效果和目标；新增目标感知卡牌描述；修复修改自定义卡后旧卡实例仍使用旧效果的问题。
- 玩法描述：带数值的卡牌效果在次数大于 1 时，会在句号左侧显示次数，例如“造成1点伤害3次。”；次数为 1 时不额外显示次数，只保留句号。多次伤害中，单条效果的数值表示每一次的基础值，次数表示该效果会重复结算几次；同一重复批次内的多个效果会在同一次动画中一起发生。
  - 代码实现：`CardRenderHelper.descriptionLines()` 先生成不带句号的效果正文，再通过次数后缀统一追加“X次。”；`BattleState.applyCard()` 按 repeat index 生成待结算批次，每个批次包含当前 repeat 仍有效的所有效果。
  - 变更记录：保证多次效果的次数位置稳定在句号左侧，次数为 1 时保持简洁描述。
- 玩法描述：实际结算多目标效果时，每个目标都独立结算伤害、格挡和状态；伤害类效果会对每个目标分别按该目标的本回合速度修正。卡牌数值预览只有在当前效果最终只会影响一个确定目标时才按该目标速度改变；需要指向目标的单体牌必须已经拖拽并实际指向合法目标才会使用该目标速度，未指向时显示基础数值。如果会影响多个目标，或随机目标在预览阶段无法确定具体对象，则显示基础数值。随机目标如果当前合法目标只有一个，就按单目标处理并在使用卡牌时高亮所有可能被随机选中的目标；当随机目标效果有多次触发时，每一次触发都会重新随机目标。
  - 代码实现：`BattleState.addEffectSteps()` 按 repeat index 生成待结算批次，每次 repeat 都重新调用 `targetsForEffect()`；`targetsForEffect()` 遇到 `RANDOM_ENEMY` 或 `RANDOM_ALLY` 时从当前候选目标中随机抽取一个目标，因此多次触发会逐次重抽。`CombatantState.applyCardDamage()` 在每个目标收到每次伤害时按攻击者和该目标速度修正；`BattleScreen.cardValues()` 按效果行生成预览数值，只有 `targetIdsForEffectTarget()` 得到单个目标且显式目标牌已经悬停到该目标时才调用速度预览。
  - 变更记录：修复多次伤害描述不随单目标速度变化的问题，同时避免多目标卡因个体目标差异显示误导性的单个修正数值；随机目标的多次触发改为每次触发独立重抽目标。
- 玩法描述：开发者编辑卡牌效果时，可按顺序添加造成伤害、获得格挡和施加流血，用数字输入框调整每条效果数值，并为每条效果指定自身、单体敌方、全体敌方、全体单位或随机敌方目标；卡牌最终战斗表现由这些效果和目标共同决定。
  - 代码实现：`DeveloperCardEffect` 记录开发者效果种类、数值和 `CardTarget`，保存/渲染时转换为统一的 `CardEffect` 列表；旧的攻击、格挡和流血字段仍会迁移为带默认目标的效果列表读取。`DeveloperCenterScreen` 在效果行中提供目标按钮并循环切换目标。
  - 变更记录：卡牌开发者数据从固定攻击/格挡/流血输入扩展为可增删、可指定目标的效果列表；清理不再使用的旧攻击/格挡/流血字段语言键。

## 抽牌、费用与卡牌关键词

- 玩法描述：卡牌可以通过“抽牌”效果让目标从自己的抽牌堆抽指定张数；目标为自己时描述为“抽%s张牌”，目标为其它对象时描述为“使目标抽%s张牌”。卡牌也可以通过“获得费用”效果让目标获得指定点费用；获得费用只增加当前费用，不改变费用上限，因此可以显示并使用超过上限的费用，例如 `5/3`。费用关键词说明为“打出手牌需要消耗费用”。
  - 代码实现：`CardEffectKind.DRAW_CARDS` 和 `GAIN_ENERGY` 使用 `CardTarget` 解析目标，`BattleState.applyPendingCardBatch()` 分别调用目标自己的 `BattleDeck.draw(...)` 和 `CombatantState.addEnergy(...)`；`CombatantState` 将费用状态改为当前费用与最大费用分离保存，`resetEnergy()` 只在行动方自己回合开始时把当前费用恢复到上限，`addEnergy()` 不修改上限。
  - 变更记录：新增抽牌与获得费用效果，支持自身、单体、群体和随机目标；当前费用允许超过最大费用并继续按现有出牌消耗规则使用。
- 玩法描述：带有“固有”的牌每场战斗开始时出现在手牌中，不被算为被抽取的牌；每有一张固有牌，开局普通抽取的牌少一张。固有牌超过 10 张时，前 10 张进入手牌，超过 10 张的固有牌直接进入弃牌堆。
  - 代码实现：`BattleDeck.startTurn(...)` 在首次回合开始时调用固有牌迁移逻辑，把 `INNATE` 牌从抽牌堆直接移入手牌或弃牌堆，再按 `STARTING_HAND_SIZE - 固有进手数量` 调用普通抽牌；该迁移不经过 `draw(...)`，因此不计为抽取。
  - 变更记录：新增固有关键词和开局固有牌处理规则。
- 玩法描述：带有“保留”的牌在回合结束时不会被丢弃，会留在手牌中进入下次自己的回合。带有“虚无”的牌如果回合结束时仍在手中，会先播放消耗表现并进入消耗堆；被消耗的牌将在战斗结束前被移除出牌组。虚无不是保留，已经被打出或进入其它牌堆的牌不会再因虚无额外触发。
  - 代码实现：`BattleDeck.discardHand(true)` 在行动方自己回合结束时按虚无、保留、普通弃牌的顺序处理当前手牌：`ETHEREAL` 进入 `exhaustPile`，`RETAIN` 留在 `hand`，其它牌进入 `discardPile`；`BattleScreen.syncSnapshotAnimations(...)` 先为离开手牌且已进消耗堆的牌创建原地淡出动画，再为其它离手牌创建弃牌/出牌动画。
  - 变更记录：新增保留与虚无关键词，回合结束手牌清理改为关键词感知逻辑，并保证虚无消耗表现优先于普通弃牌表现。

## 怪物卡组与属性维护入口

- 玩法描述：幻翼、恼鬼、卫道士等专属默认卡组的完整清单已迁移到 `docs/monster_decks.md`。这些怪物仍保留对应的玩家可见战斗风格和世界表现；纯动画细节由 `docs/battle_animation.md` 维护。
  - 代码实现：默认卡组由 `MonsterDeckProfile` 提供，卡牌定义由 `MoonSpireCardRegistry` 注册，专属世界表现继续走 `BattleVisualEvent` 与客户端视觉状态链路。
  - 变更记录：移除本文档中的默认卡组明细，避免与 `docs/monster_decks.md` 重复维护。

## 本次界面交互补充

- 玩法描述：战斗中查看抽牌堆、弃牌堆或战斗牌组时，卡牌网格覆盖层会成为真正的模态界面；底层战斗 UI 不再更新 hover、不再显示手牌或怪物意图悬停反馈，也不会接收出牌、结束回合、目标选择、相机拖动或取消战斗输入。右键或 ESC 只关闭当前覆盖层。覆盖层使用暗色透明背景，世界画面仍应可见，卡牌网格会尽量利用整个可用屏幕高度。
  - 代码实现：`BattleScreen.render()` 在 `pileOverlay` 打开时清空拖牌、相机拖动、待选目标、手牌 hover、怪物意图 hover 和 `ClientBattleState` 的 hover 目标，只渲染 `CardGridPanel` 覆盖层；鼠标、滚轮和键盘事件也优先由覆盖层消费，并为战斗内覆盖层传入 0 底部预留。
  - 变更记录：修复牌组系列覆盖层打开后底层战斗界面仍会交互或显示 hover 状态的问题，并保留暗色透明覆盖层和全高网格。
- 玩法描述：卡组、抽牌堆、弃牌堆中的卡牌网格按固定间距自然排布并连续滚动，不再按页或按整行跳转；网格优先一行显示 5 张卡，只有内容显示不完整时才出现滚动条，滚动条可以直接拖拽。滚动到顶部或底部后会直接停在合法范围内，不再越界或回弹。悬停卡牌时，放大卡牌以原卡牌中心为锚点平滑放大显示，原位小卡不再同时绘制；鼠标处于当前放大卡牌本体、原位小卡或小范围滞后区内时会保持当前放大卡牌，不会因为指向相邻卡牌交界处而在两张卡之间反复切换；放大状态下滚动时，放大卡牌会直接跟随原卡牌移动，不再有位置延迟；离开悬停后会平滑缩回原位。卡组查看界面没有底部按钮和按钮预留，底部不再有额外遮挡；顶部遮挡只保留到牌组标题和数量下方。卡牌列表顶部和底部会提供只作为空位的额外滚动距离，使第一行和最后一行卡牌滚动后也能完整显示放大预览。放大卡牌本体不再被牌堆/卡组上下边界夹动。关键词提示在右侧空间不足时会显示在卡牌左侧，并尽量相对卡牌高度居中，只有超过卡牌顶/底或屏幕边缘时才上下修正，不能反向挤动放大卡牌本体。
- 代码实现：`CardGridPanel` 使用像素级 `scrollOffset`、内容高度、可见高度和滚动条滑块映射计算滚动位置；`layout()` 按 5 列目标计算网格卡牌缩放，把裁剪起点固定在标题和数量下方，并把顶部/底部预览空间计入滚动内容高度而不是裁剪遮罩；`cardBounds()` 在卡牌行前加入顶部滚动空位，`previewBounds()` 直接用原网格卡中心计算放大卡中心，`hoveredCardIndex()` 先检测当前预览本体、原位小卡和小范围滞后区，再检测其它小卡命中，让当前预览拥有 hover 保持优先级；`PreviewAnimation` 在渲染帧中按真实帧间隔推进悬停放大，并在检测到滚动偏移变化时直接把预览中心贴到最新目标位置；`constrainScroll()` 直接将滚动值限制在 `[0, maxScroll]`，渲染和命中检测都使用 clamp 后的偏移；`CardRenderHelper.renderKeywordTipsBeside(..., screenH)` 根据屏幕剩余空间选择提示框左右位置并做竖向居中与边界修正；卡牌预览绘制使用不裁剪卡图的路径，避免缩放后把物品图卡掉。
- 变更记录：卡牌网格改为优先 5 列显示，补齐悬停放大的上下预留与平滑动画，保留原位放大和跳过原小卡的叠亮修复；滚动时放大卡牌的位置即时跟随原卡牌，避免已放大卡牌追赶滚动位置；并优化关键词提示左右选边与竖向定位。
- 玩法描述：所有卡组/牌堆查看界面的卡牌默认按卡牌显示名称排序；战斗中右键查看参战实体时显示该实体完整战斗牌库（手牌、抽牌堆和弃牌堆），不再只显示当前手牌。该排序只影响查看顺序，不改变抽牌、弃牌、消耗或结算顺序。
- 代码实现：`CardGridPanel.setCards(...)` 在接收内容后按当前客户端翻译出的卡牌名称排序；`BattleScreen` 右键实体时打开 `ENTITY_DECK` 覆盖层，并通过带 `entityId` 的 `RequestBattlePilePayload` 按需请求该实体 `BATTLE_DECK` 内容；`BattleManager` 和 `BattleState.pileCardsFor(..., entityId)` 从对应 `CombatantState` 收集手牌、抽牌堆和弃牌堆，再由 `BattlePileContentsPayload` 返回给客户端缓存。
- 变更记录：修复怪物配置多于 5 张卡时右键查看只看到当前 5 张手牌的问题，并让牌组/牌堆默认按牌名显示。
- 玩法描述：制卡台的可转换物品列表也使用一致的滚动条行为；只有列表显示不完整时才显示滚动条，支持滚轮连续滚动和拖拽滚动条，滚到边界后直接停止，不再越界或回弹。
  - 代码实现：`CardForgeScreen` 将可转换物品列表收集为独立条目，使用像素级 `scrollOffset`、可拖拽滚动条和裁剪区域渲染列表；滚轮和拖拽都会通过 `constrainScroll()` 直接 clamp 到合法范围。
  - 变更记录：取消制卡台列表边界回弹，并在内容完整显示时不再响应滚轮移动内容。
- 玩法描述：查看怪物意图小卡时，放大预览会以原小卡中心为基准原地放大；只有小卡和放大卡牌本体能保持悬停并阻止底层目标选择，右侧或左侧关键词提示区域不算作 hover 范围。
  - 代码实现：`BattleScreen.intentPreviewBoundsForSmallCard()` 根据原小卡位置计算预览卡牌本体区域，`IntentPreviewBounds.contains()` 只检测卡牌宽高，不再包含关键词提示宽度；`targetingBlockedByUiAt()` 继续拦截意图小卡和预览卡牌本体。
  - 变更记录：修复怪物意图牌放大漂移和关键词提示区域继续保持 hover 的问题。
- 玩法描述：战斗手牌以更紧密的底部弧形扇排显示，卡牌之间保持明显重叠；中间卡牌更高更正，两侧卡牌更低、更倾斜并可被屏幕底部裁切；少量手牌保持紧凑平滑，多手牌再逐步展开弧形。悬停时卡牌从原位上抬放大，周围卡牌不额外错位。
  - 代码实现：`BattleScreen.handLayout()` 按卡牌宽度和可用宽度共同计算重叠间距，并用非线性弧度控制上下落差、边缘旋转和底部裁切；`previewBounds()` 根据原手牌中心计算上抬后的预览位置，`renderHand()` 悬停时跳过原小卡再绘制预览。
  - 变更记录：继续压紧战斗手牌扇排规律，让视觉上更接近原作截图中的重叠弧线。
- 玩法描述：查看怪物意图牌时，放大预览会以原小卡中心为基准平滑原地放大，并在离开 hover 后平滑缩回原位；在接近屏幕边缘时轻微回收，避免卡牌顶部或侧边出屏。关键词提示仍不参与 hover 保持，并尽量在卡牌左/右侧相对卡牌高度居中显示。
  - 代码实现：`BattleScreen.intentPreviewBoundsForSmallCard()` 根据原小卡位置计算预览卡牌本体区域，并在 8 像素安全边距内 clamp；`PreviewCardAnimation` 在渲染帧中推进怪物意图预览缩放，`IntentPreviewBounds.contains()` 只检测卡牌宽高，不再包含关键词提示宽度；`CardRenderHelper.renderKeywordTipsBeside(..., screenH)` 负责提示框左右选边和竖向修正。
  - 变更记录：怪物意图牌悬停预览改为与卡组/牌堆网格一致的平滑放大表现，并优化关键词提示定位。

## 开发者数据与自定义内容

- 玩法描述：开启开发者模式且拥有 2 级管理员权限的玩家可以打开开发者中心。开发者中心创建的自定义卡牌是全局可调用数据，默认不会自动进入玩家卡组；管理员需要使用 `/moonspire give_card <card_id> [target]` 才能把指定自定义卡加入玩家收藏。怪物覆盖配置可以直接引用统一卡牌 id 作为怪物卡组，自定义卡支持 `custom_<自定义id>` 和旧式 `<自定义id>` 两种写法。
  - 代码实现：`DeveloperDataManager` 读取版本目录 `moonspire/developer/ui/developer_data.json` 并把自定义卡交给 `MoonSpireCardRegistry` 合并查询；`RequestDeveloperCenterPayload`、`DeveloperCenterPayload` 和 `SaveDeveloperDataPayload` 负责权限校验、打开和保存；`MoonSpireCommands` 的 `give_card` 指令通过统一注册入口发卡；`MonsterDeckProfile` 读取怪物覆盖卡组，`PlayerCardData.deckCards()` 仍只返回玩家实际持有卡牌。
  - 变更记录：自定义卡牌纳入统一卡牌注册视图，管理员发卡和怪物卡组引用都可使用统一 id，同时保留旧自定义 id 兼容规则。
- 玩法描述：卡牌现在分为模组本体卡、怪物牌、物品转换牌和自定义卡。模组本体卡直接写在模组代码/资源中，默认不会自动进入玩家卡组、不会由制卡台生成，也不会从其它玩法掉落；管理员只能通过 `/moonspire give_card <card_id> [target]` 发给玩家，后续可再接入奖励或掉落来源。
  - 代码实现：`CardSourceType.MOD` 标记模组本体卡，`MoonSpireCardRegistry` 预留 `builtinModCards()` 注册入口，并通过 `allCards()` 合并模组本体卡、内置怪物牌、可转换物品牌和开发者自定义/覆盖卡；`give_card` 继续通过统一注册入口发卡。
  - 变更记录：新增模组本体卡类型和全局卡牌列表筛选空间，初期不新增具体模组本体卡内容。
- 玩法描述：开发者中心卡牌页会显示所有可调用卡牌的指令 id，并允许开发者覆盖模组本体卡、怪物牌和物品转换牌的名称、费用、卡图与效果。删除一条开发者自定义或覆盖定义时会先弹出确认；删除覆盖后，内置/转换/模组本体卡恢复默认规则，自定义卡则从全局卡牌列表中移除。
  - 代码实现：`DeveloperCardDefinition` 保存开发者卡牌名称、效果列表和卡图设置；`MoonSpireCardRegistry.card()` 优先读取与全局 id 匹配的开发者定义，再回退到模组本体、怪物或转换生成规则；`DeveloperCenterScreen` 负责右侧全局卡牌库、筛选、id 显示和删除确认。
  - 变更记录：重构开发者卡牌页，新增覆盖删除确认、右侧全局卡牌库 id 显示和全部/模组本体/怪物/转换/自定义筛选。
- 玩法描述：开发者中心卡牌页可以直接把当前选中的卡牌发给当前玩家，效果等同于 `/moonspire give_card <card_id>` 发给自己，发出后会立刻同步玩家卡组和客户端收藏显示。
  - 代码实现：`DeveloperCenterScreen` 底部新增 `给予` 按钮，点击后通过 `GiveDeveloperCardPayload` 让服务端按当前卡牌的规范化 id 调用统一发卡流程，并复用 `BattleManager.syncCardData()` 和玩家附件同步。
  - 变更记录：新增开发者中心卡牌页的快捷发卡按钮。
- 玩法描述：开发者编辑卡牌效果时，可按顺序添加造成伤害、获得格挡和施加流血，并用数字输入框调整每条效果的数值和次数；只有需要目标且带有效数值的效果才允许切换目标，次数只对带数值的效果开放，默认值为 1。卡牌最终战斗表现由这些效果决定。没有效果的卡牌不再显示“由装备转化而来的卡牌”这类默认描述。
  - 代码实现：`DeveloperCardEffect` 记录开发者效果列表、目标和次数，保存/渲染时转换为统一的 `CardEffect` 列表；旧的攻击、格挡和流血字段仍会迁移为效果列表读取。`DeveloperCenterScreen` 为效果行同步生成数值框与次数框，并通过 `DeveloperCardEffect.canChangeTarget()` / `canChangeCount()` 限制可编辑字段。`DeveloperCardDefinition.defaultCard()` 和未知转换牌默认使用空描述键，卡面渲染只显示实际效果行。
  - 变更记录：卡牌开发者数据从固定攻击/格挡/流血输入扩展为可增删的效果列表；新增效果次数编辑、目标编辑限制和分次结算表现；效果数值改为键盘输入；移除无效果卡牌的通用装备转换描述。
- 玩法描述：开发者可以为非玩家生物配置覆盖血量、费用、速度、初始状态、卡组和奖励池；最终可交战资格由有效战斗卡组决定。详细默认值、显式空卡组、无效覆盖回退和奖励池规则见 `docs/monster_decks.md`。
  - 代码实现：`DeveloperMonsterDefinition` 保存生物覆盖数据；`BattleState` 在创建敌方 `CombatantState` 时优先读取覆盖血量、费用、速度和初始状态；`MonsterDeckProfile` 负责默认卡组、奖励池、覆盖清理和运行时回退。
  - 变更记录：将怪物覆盖底层规则、默认卡组清单和回退细节迁移到 `docs/monster_decks.md`。
- 玩法描述：开发者中心的生物卡组使用专用列表界面编辑。开发者在生物页点击“打开卡组”后，可以在当前卡组中单选一张卡并进行添加、删除、复制、重置或完成；添加时进入全卡牌列表，可搜索并同时选择多张卡，确认后追加到该生物卡组。当前卡组和添加列表按卡牌显示名称排序显示；删除、复制和保存仍作用于实际选中的卡牌条目。未保存覆盖的敌对怪物会在编辑器中显示默认血量、费用、速度和该生物实际默认卡组作为基础；未保存覆盖的中立和友善生物显示默认血量、费用、速度和空卡组作为基础。生物页还可以将当前覆盖重置到上一次保存状态，或经确认删除该覆盖，使该生物回到默认战斗数据。
- 代码实现：`DeveloperCenterScreen` 负责生物页属性表单、打开生物卡组界面、重置和删除确认；未覆盖字段通过临时创建对应实体读取默认最大生命和移动速度，并复用 `CardBalance.fixedEnergy()` 与战斗速度换算公式。保存回包后会保留当前选中的生物 id 和列表滚动位置；`DeveloperMonsterDeckScreen` 维护当前生物卡组列表和全卡牌多选添加列表，按名称构造显示条目但保留原列表索引用于删除/复制，并写回覆盖卡组状态；`MonsterDeckProfile.defaultDeckCardIds(...)` 只为默认敌对怪物提供编辑器回落数据。
  - 变更记录：移除怪物页手填卡牌 id 的编辑方式，新增怪物卡组编辑 UI、全卡牌搜索添加 UI、默认基础数值和默认卡组读取、保存后保留怪物选中与列表滚动状态，以及怪物覆盖重置/删除操作；本次将列表扩展为可创建 `LivingEntity` 的非玩家生物，并使中立/友善生物默认显示空卡组。

## 卡牌效果与目标

- 玩法描述：卡牌效果描述中，单次效果只显示效果正文并以句号结束；当同一效果次数为 2 次或更多时，次数显示在句号左侧，例如“造成 1 点伤害 3 次。”同一张牌的多条效果按次数批次同步结算：第 1 次动画触发所有次数至少为 1 的效果，第 2 次动画触发所有次数至少为 2 的效果，以此类推；同一批次内的伤害、格挡和流血会在同一次动画中一起生效。效果批次没有全部完成前，不能继续出下一张牌、结束回合或让怪物抢先选择下一张牌；攻击、格挡和状态反馈之间必须保留可感知的节奏，不能快到多个反馈在一瞬间挤完。新增“自身以外的所有友方”和“随机友方”目标，当前单人对战中没有合法友方时不会结算，也不会报错。
  - 代码实现：`CardRenderHelper.descriptionLines()` 先生成不带句号的效果文本，再按次数选择普通句号后缀或次数后缀；`BattleState.queueCard()` 将出牌拆成 `PendingCardBatch` 批次，`tickPendingCardBatches()` 先等待 `CARD_EFFECT_START_DELAY_TICKS`，再按 `REPEATED_EFFECT_VISUAL_INTERVAL_TICKS` 逐批调用 `applyPendingCardBatch()` 修改生命、格挡和流血快照，并发出同一时间点的 `BattleVisualEvent`；`CardTarget` 新增 `ALL_OTHER_ALLIES` 和 `RANDOM_ALLY`，`targetsForEffect()` 在当前 1v1 战斗中将其解析为空目标。
  - 变更记录：新增按动画批次结算与同批多效果同步触发，修正次数文本位置和单次不显示次数的规则，并补充自身以外友方/随机友方目标语义；放慢卡牌效果开始与重复批次间隔，让攻击等视觉反馈有稳定停顿。
- 玩法描述：卡牌可以拥有“消耗 X 张手牌”或“丢弃 X 张手牌”效果。卡牌效果必须按卡面/效果列表从上往下依次触发；手牌选择效果会暂停其后的效果，直到选择或自动处理完成，后续效果才能继续。触发时，正在打出的牌不算作可选手牌；只有剩余手牌数量大于所需数量时，玩家才会进入阻塞选择界面并必须选够指定数量后确认，所选牌分别进入消耗牌堆或弃牌堆，然后继续结算后续效果。如果剩余手牌数量不足或刚好等于所需数量，则不打开选择界面，直接按当前手牌顺序处理所有/所需手牌并继续结算。怪物触发该效果时自动从当前手牌前部处理所需数量，避免敌方回合等待玩家选择。
  - 代码实现：`CardEffectKind.EXHAUST_HAND` 和 `DISCARD_HAND` 表示手牌选择效果，`BattleState` 将卡牌拆成 `PendingCardStep` 顺序执行，只有玩家手牌数量多于 `requiredCount` 时才通过 `PendingHandSelectionSnapshot` 暂停结算并等待 `SelectHandCardsPayload`；`BattleDeck` 按 UUID 或当前手牌顺序从手牌移除并加入弃牌堆或消耗牌堆。`BattleScreen` 根据快照显示阻塞式手牌选择层，确认后向服务端提交选中 UUID；客户端表现上，丢弃牌飞向弃牌堆，消耗牌在原地播放消耗动画，当前实现为淡出，不飞向消耗牌堆。
  - 变更记录：新增消耗手牌和丢弃手牌两种可配置卡牌效果，并保持原有“消耗”关键词仍表示打出的卡牌自身在结算后进入消耗牌堆；新增刚好够数量时自动处理的规则，固化消耗手牌原地淡出的表现。

## 战斗动画输入锁

- 玩法描述：卡牌效果动画、批次结算或手牌选择尚未完成时，玩家不能再次触发出牌、结束回合或操作底层战斗界面。玩家刚松手打出卡牌后，该牌会立刻从手牌视觉列表中隐藏并进入飞行动画；即使服务端快照还没回传，原手牌位置也不会短暂闪回。
  - 代码实现：`BattleSnapshot` 携带 `resolvingEffects` 和 `PendingHandSelectionSnapshot` 状态，`BattleState.snapshot()` 在存在 `PendingCardBatch`、后续结算步骤或等待手牌选择时置为 true；`BattleScreen` 在该状态、手牌选择层打开或等待上一张 `UseCardPayload` 回包时，让 `playable()` 返回 false。`BattleScreen.playDraggedCard()` 记录本地已提交使用的卡牌 UUID，`visibleHandCards()` 在等待回包期间隐藏这些 UUID，并在新快照确认后清理。
  - 变更记录：修复效果动画进行中仍能触发客户端出牌动画，导致手牌返回但已播放进入弃牌堆动画的问题；新增手牌选择期间的底层输入锁；修复出牌后服务端回包前原手牌位置闪烁的问题。
## 手牌消耗与丢弃

- 玩家可以在卡牌中配置“消耗 X 张手牌”和“丢弃 X 张手牌”两类效果。
- 这两类效果严格按卡牌效果列表从上往下结算，不能被后续重排打乱。
- 触发时，当前打出的牌不计入可选手牌。
- 如果可选手牌数量大于需求，会打开阻塞式手牌选择界面；选够后确认，客户端会立即本地关闭选择层、隐藏所选手牌并播放所选牌的丢弃/消耗动画，同时继续锁住底层出牌、结束回合、目标选择和牌堆入口。服务端仍会按当前权威手牌和 pending selection 校验 UUID，校验通过后所选牌分别进入消耗堆或弃牌堆，然后继续后续效果；确认后客户端会把服务端继续回传的同一个选择要求视为尚未处理完成的旧状态并保持本地等待，不会弹回确认界面，只有服务端清除 pending 或返回不同的选择要求时才结束等待或重建界面。
- 如果可选手牌数量不足，或刚好等于需求，则不打开选择界面，直接按当前手牌顺序处理所有需要的牌并继续结算。
- 对玩家目标时，选择界面会阻塞底层战斗输入；对怪物或不可交互目标时，服务端会自动按当前手牌顺序处理，不等待客户端选择。
- 选中的手牌会按效果播放对应表现：丢弃牌从手牌位置飞向弃牌堆，消耗牌在原地淡出，不飞向消耗堆。
- 选择界面确认后不会在客户端长时间停留在禁用状态；如果服务端拒绝或返回仍需选择的新快照，客户端会重新显示选择界面并允许再次确认。如果等待期间候选手牌发生变化，服务端会重新按当前仍有效的候选手牌校验，无法继续选择且剩余候选数量不大于需求时自动完成处理，避免丢弃/消耗流程卡在选择界面。
- 开发者自定义里允许修改这两类效果的目标，但次数固定为 1。

代码上由 `CardEffectKind.EXHAUST_HAND`、`CardEffectKind.DISCARD_HAND`、`BattleState.queueCard()`、`PendingHandSelectionSnapshot`、`SelectHandCardsPayload`、`BattleDeck`、`BattleScreen.HandSelectionOverlay`、`BattleScreen.HandSelectionConfirmation` 和开发者编辑器共同实现；卡牌描述与翻译键也已补齐。`SelectHandCardsPayload` 会把确认操作排入服务端主线程，确认通过后由 `BattleState.confirmHandSelection()` 立即移除所选手牌并放入对应牌堆，再通过 `BattleManager.sync()` 同步权威快照；等待玩家选择或等待后续动画批次时，服务端不再每 tick 反复发送完整旧快照，只在确认、视觉事件或常规空闲节奏中同步，避免旧 pending 快照堆积造成确认结算延迟。`BattleManager.sync()` 为每次权威快照写入递增序号，客户端 `ClientBattleState` 会忽略同一场战斗中倒序到达的旧快照，避免已确认的手牌选择被旧 pending 状态覆盖。`HandSelectionConfirmation` 只负责客户端确认后的本地等待、输入锁和快照回包协调，不改变服务端权威结算规则。

## 卡组与列表性能

- 卡组、怪物卡组、开发者卡牌列表打开时，会缓存筛选结果，减少重复遍历和重复文本计算。
- 卡牌网格在内容未变时会缓存卡牌列表、布局和可见索引范围，并在打开卡组/牌堆时预热首屏卡牌的卡面、文字换行、动态贴图和物品图标路径。
- 怪物卡组和开发者中心的搜索结果会按查询缓存，降低打开大列表时的卡顿。

代码上主要由 `CardGridPanel`、`DeveloperFaceApplicationScreen`、`DeveloperMonsterDeckScreen` 和 `DeveloperCenterScreen` 的缓存逻辑实现。
## 本次多人战斗修正规则补充

- 玩法描述：玩家在玩家方回合按下结束回合后，立即进入等待状态，不能继续出牌，也不能再次通过快捷键重复结束回合；只有当前阶段仍是玩家方回合且本地玩家未结束、未假死亡时，结束回合按钮和 Q 快捷键才会生效。怪物方回合不会显示“等待其他玩家结束回合”。打开卡组、抽牌堆、弃牌堆或消耗牌堆界面时，遮罩会吞掉底层结束回合点击和快捷键输入；玩家刚使用卡牌并等待权威快照或出牌动画时，也会临时锁住结束回合输入。如果战斗状态本身仍允许结束回合，底层按钮文案仍显示“结束第 X 回合”，只是当前不能点击。
  - 代码实现：`BattleScreen` 使用 `canEndTurn()`、`endTurnButtonLabel()`、`endTurnVisualEnabled()`、`awaitingEndTurnSnapshot` 和 `cardActionsLocked()` 在本地立刻锁定出牌与结束回合输入；`endTurnButtonLabel()` 将战斗状态锁、牌堆遮罩输入锁和 `awaitingUseCardSnapshot` 出牌等待锁分开处理，后两者只阻断点击，不把底层可结束回合文案改成敌方回合。本地等待结束回合回包时会记录发起回合号，并在收到敌方回合、本地已结束或新玩家回合快照时清除，避免上一回合的禁用态残留到下一回合。`BattleScreen.keyPressed()` 在牌堆遮罩打开时吞掉 Q 快捷键；`ClientEvents.keyInput()` 在没有战斗 Screen 处理输入时才兜底发送 `EndTurnPayload`，且仍要求 `BattlePhase.PLAYER_TURN`、本地玩家未结束、未假死亡。服务端 `BattleState.usePlayerCard()` 仍保留 `endedTurn()` 校验作为最终保护。
  - 变更记录：修复多人回合中自己已结束但其他玩家未结束时仍能继续出牌的问题，并修复怪物方回合显示等待玩家文案的问题；本次修复新玩家回合开始时结束回合按钮文案已显示可结束、但按钮仍沿用上一回合本地等待禁用态的问题。
- 玩法描述：手牌上限为 10。抽牌时如果手牌已经达到 10 张，之后抽到的卡牌不会进入手牌，而是直接进入弃牌堆。
  - 代码实现：`CardBalance.MAX_HAND_SIZE` 定义上限，`BattleDeck.draw()` 在每次抽牌后检查 `hand.size()`，超过上限的抽牌结果加入 `discardPile`。
  - 变更记录：新增手牌上限与超额抽牌丢弃规则。
- 玩法描述：假死亡的生物在战斗中不再显示战斗条目、世界头顶血条/意图/状态/伤害数字，也不能再被选中或作为卡牌指向目标；死亡动画仍会播放。若玩家假死亡后的真实死亡被其它机制抵挡或取消，战斗收尾会清除假死亡并恢复玩家控制和战斗前状态，避免玩家永久停留在假死观战状态。
  - 代码实现：`BattleScreen`、`BattleWorldOverlay` 的条目渲染、世界命中检测和目标高亮都过滤 `fakeDead`；`CombatantState.clearFakeDeath()` 与 `BattleState.recoverBlockedPlayerDeath()` 在真实死亡结算未使玩家死亡时恢复状态；客户端 `ClientBattleState` 在快照显示实体不再 `fakeDead` 或战斗关闭时清除该实体残留的 `deathTime`，让被抵挡/取消真实死亡后仍存活的玩家立即退出死亡动画。
  - 变更记录：修复死亡目标仍可选、仍显示世界信息，以及外部死亡保护导致玩家永久假死的问题。
- 玩法描述：多种怪物同时参战时，当前选中或指向哪只怪物，就显示哪只怪物自己的意图；不同怪物不再共用第一只怪物的意图和预览数值。多人战斗中右键查看参战玩家或怪物条目时，显示被查看实体自己的完整战斗牌库，而不是固定显示本地玩家、第一只怪物或当前手牌。
- 代码实现：`BattleSnapshot` 新增 `BattleEntityCardsSnapshot` 列表并提供 `handCardsFor(entityId)` 供意图和当前手牌表现读取；`BattleState.entityHandSnapshots()` 同步所有参战实体当前手牌；`BattleScreen.currentIntentEntityId()`、`intentCardsFor()` 和 `renderIntentSummary()` 按实体 id 读取对应怪物意图；右键查看完整战斗牌库则通过带 `entityId` 的牌堆请求按需读取对应实体牌库，避免把完整牌库塞进常规快照。
- 变更记录：修复多怪物只显示第一种怪物意图、多人查看卡组只显示自己的问题，以及右键怪物卡组只显示当前手牌的问题。
- 玩法描述：开发者修改卡牌、卡面或怪物数据并保存后，服务器会把最新开发者数据同步给所有在线玩家；非管理员只刷新本地卡牌渲染/数值数据，不会自动打开开发者中心。
  - 代码实现：`SaveDeveloperDataPayload` 保存后向在线玩家广播 `DeveloperCenterPayload`；该载荷新增 `openScreen` 标志，客户端 `DeveloperDataManager.setClientData()` 只刷新本地缓存，管理员需要时才打开开发者中心界面。
  - 变更记录：修复多人环境中一名玩家修改卡牌后其它玩家看不到最新卡牌数据的问题。

## 本次卡牌效果与战斗状态补充

- 玩法描述：玩家或其它非唤魔者类人生物使用“尖牙直线”“尖牙环阵”“召唤恼鬼”时，不再播放普通左右键挥手，而是在施法窗口内保持唤魔者式双臂抬起施法姿态；唤魔者实体仍使用原版施法状态。
  - 代码实现：`BattleState` 仍通过 `BattleVisualEvent.AnimationType.EVOKER_FANG_LINE`、`EVOKER_FANG_CIRCLE` 和 `EVOKER_SUMMON_VEX` 同步施法视觉事件，并继续让 `SpellcasterIllager` 使用原版 `IllagerSpell` 与 `spellCastingTickCount`。`ClientBattleState.VisualState` 保存 `evokerSpellTicks` 作为持续施法窗口；`ClientEvents.playVisualEvents()` 不再把非唤魔者施法事件纳入 `LivingEntity.swing(...)`，`ClientEvents.suppressDefaultBattleArmPose(...)` 在非 `SpellcasterIllager` 的 Humanoid 施法时改用 `MoonSpireArmPoses.evokerSpellcasting()`。`META-INF/enumextensions.json` 扩展 `HumanoidModel.ArmPose`，`MoonSpireArmPoses` 复刻原版唤魔者施法双臂旋转公式。
  - 变更记录：修复玩家使用三张唤魔者法术牌时只出现普通挥手的问题，补齐非唤魔者 Humanoid 的持续施法姿态，并保持唤魔者原版表现不变。
- 玩法描述：拥有独特世界动画的卡牌不会再额外播放原版挥手动画，动画命中后产生的伤害、格挡、治疗和状态反馈也不会再次触发挥手。进入战斗时会取消参战实体进入前残留的使用物品、挥手和攻击动画状态；战斗中临时显示的手持物品与使用姿态只在对应卡牌动画期间存在，动画结束、退出战斗、登出或换世界时都会恢复，避免旁观其它玩家时看到残留的弓、弩、箭或抬手姿势。弓卡牌蓄力时会尽量复用原版拉弓使用姿态并在射击时播放原版箭射出音效；弩卡牌装填时会尽量复用原版装填姿态，播放原版装填开始、中段、结束音效，装填完成到射击前会举弩瞄准并播放原版弩射击音效。
  - 代码实现：`BattleVisualEvent.AnimationType` 不为 `NONE` 的事件在客户端不再触发 `LivingEntity.swing(...)`，服务器动画完成后的结算反馈不再携带出牌手持物品或卡牌展示，避免命中结算再次被识别为普通出牌视觉；没有特殊世界动画的普通伤害牌直接发送 `AnimationType.NONE` 的结算反馈，由 `ClientEvents.playVisualEvents()` 播放唯一一次原版主手挥手；`ClientBattleState.VisualState` 保存当前动画类型、手持物品和使用姿态时间，并按客户端 tick 推进，避免渲染帧率过高时提前结束；`ClientEvents` 在动画期间持续覆盖主手物品并维持原版 `startUsingItem(...)` 状态，让玩家渲染器通过 `ItemStack.getUseAnimation()`、`getUsedItemHand()` 和 `getUseItemRemainingTicks()` 自行选择弓/弩姿势，弩装填结束后的短暂瞄准阶段通过 `DataComponents.CHARGED_PROJECTILES` 复用原版已装填判定；视觉结束后恢复原状态，并在进入/退出战斗和登出时清理所有临时手部状态；战斗活跃期间客户端持续取消非战斗视觉驱动的使用物品状态，并在没有战斗视觉动画时压制参战者默认手持物品抬手姿势；`BattleState.beginBattle()` 会在服务端清除参战实体进入战斗前的使用物品和挥手状态。
  - 变更记录：修正弓、弩和近战冲击等独特战斗动画及其命中结算仍附带挥手的问题；补齐弓蓄力、弩装填/举弩的手部姿势与原版音效，修正动画期间弓弩手持物品闪烁，并清理进入战斗及观看其它玩家时的手部动画残留；本次修正普通直接伤害牌被错误归类为特殊近战冲击动画，导致默认原版挥手被跳过的问题。
- 玩法描述：掠夺者使用扣下悬刀时，会复用掠夺者自身的原版弩装填和举弩射击动画，不额外创建 Moon Spire 专属掠夺者姿势。卡牌仍按 Moon Spire 远程箭命中后结算伤害，不会调用原版掠夺者攻击逻辑生成额外真实箭伤害。
  - 代码实现：扣下悬刀的表现物品为 `minecraft:crossbow`，因此远程动画走既有 `BattleVisualEvent.AnimationType.CROSSBOW_LOAD`；客户端只在该视觉期间临时覆盖掠夺者主手弩，并同步原版 `Pillager.setChargingCrossbow(...)` 与 aggressive 状态，让原版掠夺者模型负责装填/举弩表现，视觉结束后恢复进入视觉前的状态。
  - 变更记录：本次补齐掠夺者弩牌对原版掠夺者装填/射击动画状态的兼容，并避免另造专属动画。
- 玩法描述：普通猪灵使用猪灵弩矢等弩牌时，会临时手持弩并复用原版猪灵装填/举弩姿态；猪灵和猪灵蛮兵使用近战攻击牌时，会先表现猪灵系攻击前抬手，再沿既有近战冲刺、命中和停顿链路结算。玩家或其它非猪灵实体获得这些牌后可以正常使用，但不会被强行套用猪灵专属抬手姿态。
  - 代码实现：普通猪灵弩牌的表现物品为 `minecraft:crossbow`，因此远程动画走既有 `BattleVisualEvent.AnimationType.CROSSBOW_LOAD`；`ClientEvents.syncVanillaCrossbowPose(...)` 在视觉期间同步 `Piglin#setChargingCrossbow(...)` 与 aggressive 状态，并通过 `TemporaryHandState` 在视觉结束后恢复原主手与原充能状态。`BattleState.isPiglinMeleeAttack(...)` 只在攻击者是 `AbstractPiglin`、卡牌不是远程牌且包含直接攻击伤害时选择 `LungeStyle.PIGLIN_MELEE`，由 `BattleVisualEvent.AnimationType.PIGLIN_MELEE_SWING`、`ClientBattleState.visualPiglinMeleeRaised(...)` 和 `ClientEvents.syncVisualHandOverride(...)` 驱动抬手窗口。
  - 变更记录：新增普通猪灵弩装填/举弩桥接和猪灵系近战抬手动画；猪灵专属姿态只绑定实际猪灵系攻击者，避免玩家奖励牌使用时出现错误怪物姿态。
- 玩法描述：女巫药水牌和玩家获得后的同名药水牌都使用药水专属世界动画。投掷药水牌会先临时手持对应喷溅药水并挥手，再生成只用于表现的飞行药水，药水命中目标并播放破裂/溅落反馈后才结算卡牌伤害、治疗或状态；饮用药水牌会临时手持普通药水并维持约 32 tick 原版使用姿态，饮用完成后才结算自我治疗或迅捷。女巫投掷使用女巫投掷音效，玩家和其它实体投掷使用喷溅药水投掷音效；女巫饮用使用女巫饮药音效，玩家和其它实体饮用使用通用饮用音效。视觉药水不触发额外原版药水效果。
  - 代码实现：`BattleVisualEvent.AnimationType` 末尾追加 `POTION_THROW` 和 `POTION_DRINK`。`BattleState.beginBattleAnimation(...)` 在普通远程/近战动画前识别彩色药水源物品，`PotionProjectileAnimation` 生成禁用原版命中效果的 `BattleThrownPotion` 作为视觉投射物，命中时只播放 `levelEvent(2002/2007)` 药水破裂反馈，再让战斗批次结算；`PotionDrinkAnimation` 延后到饮用 tick 完成后结算。客户端 `ClientBattleState.VisualState` 按 tick 维持药水手持和使用状态；`ClientEvents` 为女巫同步 `Witch#setUsingItem(true)` 并在视觉结束后恢复，普通 Humanoid 继续走原版物品使用姿态和对应声音。
  - 变更记录：本次新增药水投掷/饮用世界动画，并让女巫和玩家使用女巫药水牌时共享同一套延后结算的视觉链路。
- 玩法描述：卡牌现在额外支持恢复生命、获得力量、扣除力量、获得再生、获得迅捷、获得引信，以及给予中毒、烧伤、凋零、虚弱、缓慢。治疗默认作用于自己，非自身目标会在描述中写明目标；力量、再生、迅捷和引信默认作用于自己；扣除力量、中毒、烧伤、凋零、虚弱、缓慢默认作用于单体敌方。治疗会恢复战斗生命值但不超过当前有效战斗生命上限，并在目标头顶显示绿色恢复数字。
  - 代码实现：`CardEffectKind` 和 `DeveloperCardEffect.Kind` 新增对应效果并设置默认目标；`DeveloperCenterScreen` 的效果选择、保存和重新加载路径会把开发者效果转换为统一 `CardEffect`；`BattleState.applyPendingCardBatch()` 在同一批效果里结算治疗、力量增减和状态施加，`BattleVisualEvent.healedHealth`、`ClientBattleState` 与 `BattleWorldOverlay` 负责同步并显示绿色恢复飘字。
  - 变更记录：扩展卡牌效果列表、开发者中心效果列表、卡面描述、怪物意图汇总和战斗条目预览；新增“获得引信”和“给予凋零”效果。
- 玩法描述：饥饿是负面战斗状态。拥有者回合开始时少抽 1 张牌，并减少 1 层；多层饥饿表示连续多个回合开始各少抽 1 张牌，不会在同一回合按层数一次少抽多张。固有牌仍会先进入手牌，饥饿只减少本次普通抽牌数。饥饿猛扑是尸壳使用的 2 费怪物牌，造成 8 点攻击伤害，并给予目标 1 层饥饿。
  - 代码实现：`BattleEffectType.HUNGER` 绑定 `gui/effects/hunger.png` 图标与翻译键；`BattleDeck.startTurn(RandomSource, int)` 在扣除固有牌后再应用抽牌减少量。`BattleState.beginPlayerTurn()` 在玩家抽牌前消耗玩家饥饿并传入抽牌减少量；玩家回合开始为敌方预抽意图手牌时只读取敌方当前饥饿计算少抽，不消耗层数；若敌方预抽后获得饥饿，`BattleDeck.applyAdditionalStartTurnDrawReduction(...)` 会按当前饥饿重新校准本次回合开始抽牌，撤回需要少抽的一张牌，并把实际手牌变化反馈给 `BattleState` 立刻同步新的怪物意图；`beginMonsterTurn()` 在敌方回合开始时实际消耗敌方饥饿。`CardEffectKind.HUNGER` 接入卡牌结算、怪物 AI 模拟、意图评分、负面效果分类和卡牌描述/关键词提示；`MoonSpireCardRegistry` 注册 `builtin_monster_hungry_lunge`，`MonsterDeckProfile` 为尸壳在僵尸族分支前返回专属默认卡组。
  - 变更记录：新增饥饿状态、给予饥饿卡牌效果和饥饿猛扑；饥饿猛扑已接入尸壳默认卡组，默认卡组清单见 `docs/monster_decks.md`；本次修复敌方预抽后获得饥饿时怪物意图不会随少抽牌结果立即更新的问题。
- 玩法描述：力量是战斗状态，会让拥有者造成的攻击牌伤害增加等同于力量层数的点数；力量允许变为负数，负力量会让造成的攻击牌伤害减少对应点数，最低不会把基础伤害降到 0 以下。力量先计入卡牌基础伤害，再进入虚弱、速度、守护和格挡等后续结算；负力量在状态图标右下角显示红色层数，提示改为显示减少的伤害点数。
  - 代码实现：`BattleEffectType.STRENGTH` 允许负层数，`CombatantState.addEffect()` 保留负力量并移除 0 层力量；`CombatantState.applyCardDamage()` 先把攻击者力量并入基础伤害，再调用 `BattleDamageCalculator` 处理虚弱、速度与守护；`BattleScreen`、`BattleWorldOverlay` 和 `CardRenderHelper.renderEffectTip()` 统一处理负力量红字与提示文案。
  - 变更记录：新增力量状态、负力量显示和攻击伤害预览/实际结算一致性。
- 玩法描述：再生、迅捷、中毒、烧伤、凋零、虚弱、缓慢、备箭充足、引信都是战斗状态。再生在回合结束时恢复等同于层数的生命值，然后减少 1 层，但不会超过当前有效战斗生命上限；迅捷让拥有者战斗内有效速度提高等同于层数的点数；中毒在回合开始时造成等同于层数的伤害，然后减少 1 层；烧伤在回合结束时造成等同于层数的伤害，然后减少 1 层；凋零会降低等同于层数的战斗生命上限，最低降至 1，当前生命值不能超过降低后的上限，并在回合结束时减少 1 层；凋零层数减少后生命上限恢复，但不自动恢复生命值；虚弱让拥有者造成的攻击伤害降低至 75%，并在回合结束时减少 1 层；缓慢让拥有者战斗内有效速度降低等同于层数的点数；备箭充足在回合开始时每层获得一张转换卡箭；引信在回合结束时减少 1 层，归零时进入自爆流程。受到烧伤伤害时，如果目标拥有引信，会先结算烧伤伤害，再立刻清空引信并进入自爆流程。迅捷与缓慢只影响本场战斗里的有效速度，有效速度最低为 1。
  - 代码实现：`BattleEffectType` 新增这些状态；`CombatantState.roundSpeed()` 返回基础本回合速度加迅捷、减缓慢后的有效速度并限制最低 1；`CombatantState.effectiveMaxBattleHealth()` 按 `max(1, maxBattleHealth - 凋零层数)` 计算有效战斗生命上限，`addEffect(...)`、`reduceEffect(...)`、`clearEffect(...)` 会把当前生命夹到有效上限以内但不会因上限恢复而回血；`BattleState.beginPlayerTurn()`、`beginMonsterTurn()` 和 `beginRoundEnd()` 分别在行动方自己的回合开始/结束触发中毒、备箭充足、再生、烧伤、引信、凋零和虚弱衰减；状态伤害沿用 `applyEffectDamage()`，会先被格挡吸收但不受虚弱、守护和速度影响。烧伤分支在目标带有引信时调用 `triggerSelfDestruct(...)`，即使烧伤已经把目标推入假死亡，也会恢复到可自爆状态后排入待自爆队列。
  - 变更记录：补齐战斗状态触发时机、层数衰减、速度修正和状态伤害规则；本次新增备箭充足的回合开始生成箭规则、凋零降低生命上限规则，以及引信倒计时、烧伤引爆和自爆触发规则。
- 玩法描述：自爆会先播放苦力怕式膨胀和白色闪烁动画，不使用受击红闪；动画结束后再对所有其它存活单位造成当前自爆伤害，然后让自爆者死亡。自爆伤害默认 30 点，会被目标格挡吸收，不受守护、虚弱和速度影响。自爆杀死任意玩家时，玩家会正常进入假死亡；多人战斗只有玩家方全灭才失败，因此自爆没有导致所有玩家死亡时不会立即失败。单人玩家自爆杀死怪物但自己死亡时，仍按玩家方全灭判负。自爆视觉只表现卡牌战斗爆炸，不破坏世界方块。
  - 代码实现：`CardBalance.SELF_DESTRUCT_DAMAGE` 是自爆数值唯一来源，`BattleState.triggerSelfDestruct(...)` 清空引信、发送 `SELF_DESTRUCT` 视觉事件并加入 `PendingSelfDestruct`；`tickPendingSelfDestructs()` 等动画 tick 结束后调用 `resolveSelfDestruct(...)`，先遍历 `allStates()` 中除自爆者外的存活单位并调用 `applyEffectDamage(...)`，随后调用 `killForSelfDestruct(...)` 让自爆者死亡。自爆等待期间 `hasPendingCardBatches()` 会阻塞下一步战斗结算，`shouldSyncNow()` 不重复发送空的等待快照；`BattleState.hasWinner()` 继续按玩家方或怪物方全灭判定胜负。`BattleVisualEvent.AnimationType.SELF_DESTRUCT`、`ClientBattleState.VisualState` 和 `ClientEvents` 播放膨胀、独立白色闪烁渲染层、苦力怕预爆音效、爆炸音效和爆炸粒子，不调用原版世界爆炸，也不把自爆闪烁写入实体受击红闪状态。
  - 变更记录：新增统一自爆结算、多人判负继承规则和无世界破坏的自爆视觉；本次将自爆伤害与自爆者死亡延后到自爆动画结束后结算。
- 玩法描述：所有新增状态都能在战斗条目、世界头顶 UI、卡牌关键词提示和怪物意图里显示；攻击伤害预览会把力量、负力量、虚弱、迅捷、缓慢、守护和速度纳入同一条计算链，显示值与实际结算保持一致。拥有凋零时，生命条显示降低后的有效战斗生命上限。
  - 代码实现：`BattleCombatantSnapshot` 同步新增状态层数，并把凋零后的有效战斗生命上限写入 `maxHealth`；`CardRenderHelper.previewAttack()` 和 `BattleScreen.previewDamageAmount()` 使用攻击者与防守者快照计算预览；`BattleScreen` 和 `BattleWorldOverlay` 按效果正负分类汇总怪物意图；`zh_cn.json` 与 `en_us.json` 补齐卡牌效果、状态名称、状态说明、开发者中心效果名称和目标化描述翻译键。
  - 变更记录：同步客户端渲染、翻译资源、关键词提示和怪物意图展示。
- 玩法描述：卡牌现在支持“远程”“消耗箭造成伤害”“箭”“给予发光”和“保留时减少耗费”。远程伤害无视速度造成伤害，但仍受力量、虚弱、守护、发光和格挡影响；远程弓弩伤害不再立即结算，而是在使用者完成蓄力或装填、箭穿过路径并命中目标后才造成伤害与附加效果。消耗箭造成伤害会从手牌中消耗一张带“箭”关键词的牌，没有箭时该牌没有任何效果但仍会支付费用并进入弃牌堆，只有一张箭时自动消耗，多张箭时只展示箭牌供选择。被消耗的箭只把自身基础伤害和附加效果附带到消耗箭的卡牌上，不附带固有、消耗、虚无、保留、远程、箭、保留降费这类影响卡牌自身的效果。保留降费在回合结束时对仍保留在手牌里的牌生效，最低降到 0，降低后的费用持续到本场战斗结束。
  - 代码实现：`CardEffectKind`、`DeveloperCardEffect.Kind`、`DeveloperCardDefinition` 和 `DeveloperCenterScreen` 增加新效果；`BattleState.queueCard()` 将 `CONSUME_ARROW` 拆成限制候选 UUID 的 `PendingHandSelectionStep`，`PendingHandSelectionSnapshot.Action.CONSUME_ARROW` 让客户端手牌选择层只显示箭牌；`resolveConsumedArrow()` 只读取被消耗箭的基础 `DAMAGE` 和 `GLOWING`、`WITHER` 等可附加效果，并把原生箭转换为普通箭视觉、原生光灵箭转换为光灵箭视觉，其它箭关键词牌回退为普通箭视觉；远程伤害批次先进入 `ProjectileAnimation`，服务器生成无伤害、无重力、无碰撞的箭实体并在命中后调用原有批次结算；`CombatantState.reduceRetainedCardCosts()` 在拥有者自己的回合结束、弃置非保留手牌前修改战斗牌实例费用。
  - 变更记录：新增远程、消耗箭、箭、发光、保留降费效果，并让无箭的消耗箭牌按“无效果出牌”处理；本次将远程弓弩伤害改为箭命中后结算，且动画箭可穿过方块并保证到达目标。
- 玩法描述：发光是负面战斗状态。拥有发光的单位受到的直接伤害增加 10%，该增伤与虚弱、速度、守护等百分比增减伤在同一乘区中相乘，最后统一四舍五入；流血、中毒、烧伤等效果伤害默认不受这些直接伤害增减影响。随机目标卡牌会优先选择合法的发光目标，多个合法发光目标中优先选择层数最高者，同层数随机。发光在回合结束时减少 1 层，拥有发光时实体显示原版发光视觉。
  - 代码实现：`BattleDamageCalculator.directDamage()` 统一处理直接伤害百分比乘区和最终 `round`；`CombatantState.applyCardDamage()` 将目标发光状态传入直接伤害计算，`applyEffectDamage()` 保持不读取这些增减伤状态；`BattleState.randomTarget()` 优先筛选发光目标并按层数排序；`CombatantState.syncEntityGlowing()` 在状态增减、回合衰减和战斗结束时同步原版实体发光标记。
  - 变更记录：新增发光状态、发光图标、实体发光表现、随机目标优先规则和直接伤害 1.1 乘区。
- 玩法描述：弓、弩、箭和光灵箭现在有固定转换牌。弓转换为 1 费“远程。消耗一根箭，造成 7 点伤害。”；使用时会朝目标满蓄力后射出箭，箭命中后才结算伤害与效果。弩转换为 2 费“远程。消耗一根箭，造成 13 点伤害。保留时减少 1 点耗费。保留。”；使用时会先装填箭，再射向目标并在命中后结算。箭转换为 1 费“箭。造成 3 点伤害。消耗。”；光灵箭转换为 1 费“箭。造成 3 点伤害。给予 1 层发光。消耗。”。
  - 代码实现：`MoonSpireCardRegistry.specialConvertedCardDefinition()` 对 `Items.BOW`、`Items.CROSSBOW`、`Items.ARROW` 和 `Items.SPECTRAL_ARROW` 返回专用 `RegisteredCardDefinition`，并继续使用物品自己的翻译键作为卡牌名称与物品图标作为卡面图案；`BattleState.rangedPrepareTicks()` 让弓准备 20 tick、弩准备 25 tick，`BattleVisualEvent.AnimationType` 让客户端显示对应持握/使用表现。
  - 变更记录：为弓、弩、箭和光灵箭添加专用转换牌，覆盖通用装备转换数值；本次为弓弩转换牌补充蓄力/装填和箭命中后结算的世界动画规则。

## 本次卡牌文本展示修正

- 玩法描述：卡牌效果描述会把每种效果的默认目标视为简写目标。治疗、获得力量、获得再生和获得迅捷默认写成作用于自己；扣除力量、中毒、烧伤、凋零、虚弱和缓慢默认写成作用于敌方单体，但不额外显示“目标”。只有开发者把效果目标改成非默认目标时，描述才会写出“目标”“全体敌方”“随机敌方”等具体范围。
  - 代码实现：`CardRenderHelper.effectDescriptionKey(CardEffectKind, String, CardTarget)` 在目标等于 `CardEffectKind.defaultTarget()` 时使用无后缀翻译 key，非默认目标才追加目标后缀；语言资源把负面状态的无后缀描述调整为“给予 X 层状态”。
  - 变更记录：修复默认给予中毒、烧伤、凋零、虚弱、缓慢等效果时误显示“目标”的问题。
- 玩法描述：同一张卡里如果同时出现获得力量和扣除力量，卡牌关键词提示只显示一份“力量”说明，不再重复展示两个相同关键词详情。
  - 代码实现：`CardRenderHelper.renderKeywordTips()` 和 `keywordTipsHeight()` 使用去重集合按关键词标识只渲染一次提示，并让高度计算与实际渲染保持一致。
  - 变更记录：修复同一关键词由多个效果触发时提示框重复的问题。
- 玩法描述：如果某个卡牌效果描述中直接出现了关键词名称，该词也必须按关键词样式显示，并在卡牌放大预览旁展示对应关键词详情。例如“消耗一根箭”里的“箭”会显示为关键词并展示“箭”的说明；“保留时减少耗费”里的“保留”也会展示保留说明。
  - 代码实现：`CardRenderHelper.descriptionLines()` 将这些词作为独立的关键词组件传入翻译文本；`renderKeywordTips()` 和 `keywordTipsHeight()` 除了读取显式关键词效果，也会把 `CONSUME_ARROW` 映射到“箭”提示、把 `RETAIN_REDUCE_COST` 映射到“保留”提示，并继续用去重集合避免重复。
  - 变更记录：新增效果描述内关键词的高亮与关键词详情展示规则。

## 本次卡牌数值与速度预览修正

- 玩法描述：手牌、放大预览和怪物意图中的攻击数值会先应用出牌者自身造成的伤害修正。即使攻击牌还没有指向具体目标，力量、负力量、虚弱这类只取决于出牌者自身的效果也会立刻改变卡牌数值；高于原始值显示为绿色，低于原始值显示为红色。只有目标速度、目标格挡和目标守护这类必须依赖具体目标的修正，才会等到目标明确后进入预览。
  - 代码实现：`BattleScreen.cardValues()` 在没有单体预览目标时仍使用中性目标环境调用 `CardRenderHelper.previewDamageAmount()`，把攻击者力量和虚弱纳入逐条伤害数值；存在明确目标时继续叠加目标速度、格挡和守护。攻击总值现在只要存在攻击效果就使用预览总和，即使负力量把总伤害压到 0 也不会回退为原始值。
  - 变更记录：修复拥有力量或负力量时未指向目标的卡牌数值不变，以及 0 伤害预览回退为原始攻击值的问题。
- 玩法描述：迅捷和缓慢改变的是战斗内有效速度，获得或失去这些状态后，战斗条目、世界头顶速度显示、伤害预览和实际结算都会立刻使用新的有效速度。
  - 代码实现：`CombatantState.snapshot()` 同步 `roundSpeed()` 的有效速度而不是原始本回合速度；`CombatantState.applyCardDamage()` 的攻防双方速度结算也统一读取有效速度。
  - 变更记录：修复获得迅捷或缓慢后界面速度与伤害计算没有立即更新的问题。

## 本次战斗同步与动画修复

- 玩法描述：战斗中右键查看怪物卡组时，显示内容跟随该怪物当前真实战斗牌库变化，包括手牌、抽牌堆、弃牌堆，以及该怪物正在使用或结算中的牌；等待服务器返回新版牌库内容时不会清空旧内容，因此弹窗不会在每次战斗快照更新时闪烁。
  - 代码实现：`BattleCombatantSnapshot` 为每个参战实体同步自己的 `deckVersion` 和战斗牌库数量；`BattleState.battleDeckCards(...)` 和 `battleDeckCount(...)` 在被查看实体拥有 `pendingUsedCard` 时把这张牌纳入战斗牌库显示和数量，但不放入抽牌堆、弃牌堆或消耗堆；`BattleScreen` 的 `ENTITY_DECK` 请求与缓存 key 使用被查看实体的牌库版本，`BattleManager.requestPile(...)` 回包时重新读取服务端真实版本；`ClientBattleState` 按 `battleId + BattlePileSource + deckVersion + entityId` 缓存内容，并允许打开中的弹窗接收不低于当前快照版本的新版回包；效果结算或手牌选择导致大快照暂时抑制时，弹窗会按客户端 tick 低频重验当前实体牌库。客户端等待新 `BattlePileContentsPayload` 时只更新数量，不把已有 `CardGridPanel` 内容清空。
  - 变更记录：修复怪物卡组查看闪烁和内容不跟随出牌、抽牌、弃牌、消耗、生成箭变化的问题；本次把正在使用/结算中的怪物牌也纳入右键牌组查看，避免出牌期间牌从牌组中短暂消失。
- 玩法描述：非远程直接伤害会让攻击者冲到目标面前并停留在打击点，不会立刻后退；如果双方已经足够接近，则只播放原地攻击和命中停顿，不再产生短距离冲刺。玩家近战牌和怪物近战牌都使用同一套表现。玩家近战冲刺由客户端按战斗视觉事件平滑显示移动和腿部摆动，不再被服务端逐 tick 传送打断；双方回合都结束、逃离或战斗结束时，服务端再把单位统一安全送回开战位置。远程弓/弩伤害继续在蓄力或装填后发射箭实体，空表现物品默认补弓。
  - 代码实现：`BattleState.LungeAnimation` 拆分为冲刺和命中停顿，停顿阶段先调用 `completeAnimatedBatch(...)` 结算伤害与反馈，动画结束后把攻击者锁定点更新到安全打击点；`beginRoundEnd()`、`resetParticipantsToStart()` 和战斗结束恢复路径负责统一安全回位。`beginBattleAnimation(...)` 仍按 `REMOTE` 区分远程投射物和近战冲刺，并通过 `BattleVisualEvent.animationStart` / `animationStrike` 把冲刺起点和打击点同步给客户端。冲刺动画拥有者是 `ServerPlayer` 时，`moveActor(...)` 只更新服务端权威位置和最终校正，不再逐 tick 调用 `connection.teleport(...)`；客户端由 `ClientBattleState.visualRenderOffset(...)` 在渲染前叠加视觉位移，并按位移增量驱动腿部行走摆动。`builtin_monster_pounce` 会按双方碰撞盒半径计算更近的贴身打击点，并在冲刺曲线上叠加 1 格跳跃高度。
  - 变更记录：修复玩家近战牌移动卡顿、腿部不自然、近距离仍然小幅冲刺、命中后立刻后退，以及用牌结束进入弃牌堆或消耗堆后牌库版本没有及时触发同步的问题；本次让扑袭使用贴身跳跃轨迹，并让动画中的世界头顶血条跟随实体真实或视觉移动。
- 玩法描述：怪物使用“弓击”时仍按近战直接伤害处理，会冲到目标前近战攻击；使用和挥击期间主手显示为弓，不会改成远程射击或额外生成箭。
  - 代码实现：`BattleState.beginBattleAnimation(...)` 对 `builtin_monster_bow_strike` 的 `MELEE_LUNGE` 视觉事件使用 `Items.BOW` 作为临时表现物品，仍通过 `LungeAnimation` 和普通非 `REMOTE` 伤害批次结算；`ClientBattleState` 与 `ClientEvents` 继续用该事件同步临时主手、近战位移和命中挥手。
  - 变更记录：让弓击使用时手持弓进行近战突进攻击。
- 玩法描述：直接伤害造成生命值损失时，目标会出现原版风格击退，并在击退落点暂时停留；格挡完全吸收、效果伤害、假死亡目标不会触发击退。击退不会被战斗锁定立刻拉回，双方回合都结束后才随其它战斗位移一起回到安全起点。
  - 代码实现：`BattleState.applyBattleKnockback(...)` 对直接伤害命中的目标调用原版 `LivingEntity.knockback(...)`，读取原版计算出的速度后转入服务端 `KnockbackState`，并清空实体残留速度，避免战斗冻结或怪物 `NoAI` 吞掉位移；`freezeEntity(...)` 在击退状态有效时由服务端使用 `MoverType.SELF` 逐 tick 推进真实实体位置，应用水平衰减、重力和落地停止，并把 `EntityLock` 持续更新到当前位置；释放结束时使用 `safeReturnPosition(...)` 把锁定点更新到当前无碰撞落点。客户端不再叠加会回弹的本地击退偏移，受击位移、头顶血条和判定位置都以服务端真实实体位置为准。
  - 变更记录：修复直接伤害只有受伤反馈、缺少真正可见击退，以及击退后被战斗锁定立即拉回的问题；本次将击退改为服务端权威运动状态，让真实实体坐标同步后成为后续锁定点，并继续移除客户端回弹式假位移。
- 玩法描述：骷髅家族使用“射击”等弓类远程牌时，会显示接近原版骷髅持弓抬手瞄准并逐步拉开的射击姿态；玩家远程牌继续使用原版物品使用姿态。该视觉只驱动原版使用状态和 Moon Spire 自己的视觉箭，不会调用原版骷髅 `performRangedAttack(...)` 生成额外真实箭伤害。
  - 代码实现：`ClientBattleState` 根据 `BOW_DRAW` 视觉事件维持临时主手弓和 20 TPS 使用时间，并在视觉使用期间返回同一个主手 `ItemStack` 实例；`ClientEvents.syncVisualHandOverride(...)` 对 `AbstractSkeleton` 同步临时弓时把客户端 `aggressive` 状态设为真，并通过访问转换公开的 `LivingEntity.useItem`、`useItemRemaining` 和 `setLivingEntityFlag(...)` 持续写入原版 using-item 状态，让 `SkeletonModel` 和弓物品模型读取稳定的拉弓进度。视觉结束后恢复原主手物品、使用状态和 aggressive 状态。
  - 变更记录：修复骷髅射击时没有按原版骷髅路径抬手拉弓的问题；本次修复弓物品本体没有进入拉开状态的问题。
- 玩法描述：删除全局自定义卡后，未来战斗、怪物自定义卡组和在线玩家收藏/牌组不会继续保留这张已经无法解析的卡；删除或缺失开发者数据文件后重新打开开发者中心，也不会把旧内存缓存里的自定义内容重新写回磁盘。怪物卡组无效引用、默认回退和显式空卡组规则见 `docs/monster_decks.md`。
  - 代码实现：`DeveloperDataManager.reload()` 在打开开发者中心时按磁盘当前状态重读数据，`sanitizeData(...)` 对卡牌、卡面和怪物覆盖去重并清理无法解析的卡牌 id；开发者中心删除卡牌时同步移除怪物卡组引用和显示名缓存。`SaveDeveloperDataPayload` 保存后遍历在线玩家的 `PlayerCardData.removeUnresolvableCustomCards()`，移除已删除或无法解析的自定义卡并通过附件同步和 `BattleManager.syncCardData(...)` 刷新客户端。怪物卡组覆盖清理和运行时回退由 `MonsterDeckProfile` 兜底。
  - 变更记录：将怪物卡组覆盖清理和回退细节迁移到 `docs/monster_decks.md`。
- 玩法描述：单位因假死亡导致手牌离开时，不会把保留牌或其它离手牌显示成中央打出卡牌；只有本地实际提交出的牌和服务端下发的出牌视觉事件会触发打出展示。本地玩家使用的牌显示在屏幕中央，自身触发自爆时 `自爆` 查看牌也显示在屏幕中央并像消耗牌一样原地消失；其他玩家、友方单位和怪物的出牌视觉事件统一显示在怪物出牌位置，其中 `自爆` 也按消耗牌规则淡出。战斗结束或回合重置把怪物放回开战位置时，会优先寻找不碰撞的安全落点，避免卡进地下。
  - 代码实现：`BattleScreen.syncSnapshotAnimations(...)` 只把 `locallyUsedCardIds` 中的离手牌视为本地出牌移除，不再因为 `resolvingEffects` 把所有离手牌都判定为打出；本地自爆视觉事件会创建一张中心淡出的 `自爆` 飞牌，`ClientBattleState.updatePlayedCardDisplay(...)` 把所有非本地出牌事件记录到怪物出牌位，并保存实际出牌者 id 供卡面数值预览使用；`ClientBattleState.monsterPlayedCardAlpha()` 对 `自爆` 和带“消耗”的牌使用同样的淡出透明度；`BattleState.resetParticipantsToStart()` 使用 `safeReturnPosition(...)` 从起点向上扫描无碰撞位置后再传送并锁定实体。
  - 变更记录：修复保留牌在自身死亡时误显示打出卡牌，以及结束战斗时怪物有概率卡入地下的问题。
- 玩法描述：怪物或其它非玩家生物在半空中进入战斗时，不会被战斗锁定永久悬停；它们和玩家一样会保留竖直下落，直到实际落地，同时仍不能在水平方向乱跑。
  - 代码实现：`BattleState.freezeEntity(...)` 在冻结非玩家参战实体水平位移时，会对因战斗 `NoAI` 停止原版 AI tick 的怪物补一次竖直重力推进，并把锁定位置的 Y 值更新到落地后的实际位置。
  - 变更记录：修复非玩家生物在半空进入战斗后不会按实际情况落下的问题。
- 玩法描述：使用会作用到非自身目标的牌时，攻击者会在卡牌世界动画开始时立刻朝向目标；目标在低处时头会低下，目标在高处时头会抬起。远程弓/弩牌不再等到效果结算后才转头，近战冲刺和命中反馈也沿用同一朝向规则。造成生命值伤害时，受击者继续使用原版风格的受击闪烁、受击方向和击退表现；普通非玩家实体没有额外手臂后仰动作时，不会用自定义姿势强行模拟。
  - 代码实现：`BattleVisualEvent` 新增 `lookTarget` 并随快照同步；`BattleState.emitVisual()` 为非自身目标写入目标眼位，`facingPointForBatch()`、`facePosition()` 和 `turnTowardPosition()` 使用带高度的朝向点计算 yaw 与 pitch，并在最终对准时同步头、身体和旧旋转。客户端 `ClientEvents.playVisualEvents()` 收到视觉事件后立即对攻击者调用原版 `lookAt(...)`，并通过统一受击 helper 调用 `LivingEntity.animateHurt(...)`，让玩家继续走原版 `hurtDir`，普通实体至少稳定触发原版受击覆盖层。
  - 变更记录：修复远程卡牌拉弓/装弩开始时没有提前转头、高低差不会影响头部俯仰，以及受击方向只依赖攻击者当前 yaw 导致玩家受击反馈生硬的问题；本次不改变伤害、格挡、击退强度或回合规则。

## 本次溺尸卡牌与三叉戟动画补充

- 玩法描述：溺尸默认卡组和卡牌清单维护入口见 `docs/monster_decks.md`。溺尸三叉戟、引雷和激流相关牌仍使用普通卡牌效果结算，可施加潮蚀、麻痹和格挡等效果，玩家和怪物使用同一套结算规则。
  - 代码实现：`MonsterDeckProfile` 负责溺尸默认卡组分流，`MoonSpireCardRegistry` 注册对应内置牌；`CardEffectKind.TIDAL_EROSION` 和 `CardEffectKind.PARALYSIS` 接入卡牌描述、怪物 AI 评分、目标预览、战斗结算和保存/加载映射。
  - 变更记录：将溺尸默认卡组明细迁移到 `docs/monster_decks.md`，保留三叉戟相关结算和动画说明。
- 玩法描述：三叉戟投掷牌在远程结算前会播放三叉戟蓄力和飞行，引雷投掷命中时稳定播放雷击视觉，不受天气、露天或雷暴条件限制，也不会造成额外原版雷电伤害或点火。溺尸使用三叉戟投掷、引雷投掷和激流冲刺蓄力时使用原版溺尸举矛姿态，不再表现为玩家式使用物品姿态。激流冲刺会先播放三叉戟蓄力，随后从攻击者身体中线进入原版激流旋转姿态和激流纹理特效；玩家使用这些内置牌时也走同一套世界动画。激流卡牌只复用原版视觉，不接管原版激流碰撞、伤害或最终位移，卡牌伤害和战斗锁定位置仍由战斗系统控制。
  - 代码实现：`BattleVisualEvent.AnimationType` 追加 `TRIDENT_THROW`、`CHANNELING_TRIDENT_THROW` 和 `RIPTIDE_RUSH`，旧枚举顺序保持不变。`BattleState.ProjectileAnimation` 在三叉戟牌中生成引导式 `ThrownTrident` 视觉实体，禁用拾取和原版伤害，由卡牌批次命中后结算战斗伤害；引雷命中点生成 `LightningBolt` 并设置 `setVisualOnly(true)` 和 0 伤害。`ClientBattleState` 在激流蓄力结束后才标记激流旋转阶段，释放冲刺时按攻击者包围盒半高把客户端渲染路径抬到身体中线，并在进入战斗时固定相机锚点眼高；`ClientEvents` 为参战者临时写入主手三叉戟、原版 spin flag、`Pose.SPIN_ATTACK` 和激流物品字段，玩家战斗视觉不调用 `startAutoSpinAttack(...)`，避免触发原版激流运动和相机副作用，溺尸额外通过渲染层使用原版激流纹理表现旋转特效；服务端 `RiptideRushAnimation` 只负责等待和命中结算时机，并把视觉打击点修正到安全无碰撞落点，战斗锁不逐 tick 传送玩家。
  - 变更记录：远程三叉戟、引雷雷击和激流冲刺都接入现有 `BattleVisualEvent` 与客户端世界视觉状态；溺尸三叉戟蓄力改为原版举矛姿态；玩家与溺尸共享激流视觉表现，但不使用原版激流伤害、碰撞结算或 auto-spin 运动状态。
- 玩法描述：流血等战斗状态造成伤害时，只显示伤害数字、受击音效和受击反馈，不再让伤害来源或受伤者触发出牌挥手动作。普通近战牌、远程牌、格挡牌和荆棘反馈仍按各自原有动画规则表现。
  - 代码实现：`BattleState.applyPendingCardBatch()` 会区分普通卡牌伤害和 `effectDamage` 状态伤害。纯状态伤害视觉事件不携带 `playedCard` 或临时手持物，且使用 `AnimationType.NONE` 作为命中反馈，因此 `ClientEvents.playVisualEvents()` 只播放受击反馈，不会把它识别为普通出牌挥手。
  - 变更记录：修复溺尸或其它参战者受到流血回合伤害时出现挥手的问题，同时保留状态伤害的数字、音效和受击动画。

## 本次烈焰人与恶魂专属卡组补充

- 玩法描述：烈焰人和恶魂默认火球卡组维护入口见 `docs/monster_decks.md`。这些内置牌仍按普通卡牌效果结算伤害、烧伤、虚弱、格挡、相位和守护；玩家之后获得这些牌时使用同一套结算规则。
  - 代码实现：`MonsterDeckProfile` 分别为 `EntityType.BLAZE` 和 `EntityType.GHAST` 返回默认卡组，`MoonSpireCardRegistry` 注册对应 `builtin_monster_*` 内置牌；远程火球牌带 `CardEffectKind.REMOTE`，卡牌 id 兼容继续由 `CardInstance.normalizeCardId(...)` 处理。
  - 变更记录：将烈焰人和恶魂默认卡组明细迁移到 `docs/monster_decks.md`，保留火球视觉规则说明。
- 玩法描述：烈焰人和恶魂使用火球类远程牌时，会先短暂播放原版风格蓄力状态，再发射无碰撞、无额外伤害的视觉火球。烈焰人显示小火球与原版着火/蓄力外观，恶魂显示大火球与原版愤怒开口外观；火球命中只播放视觉粒子和音效，实际伤害、烧伤或虚弱只按卡牌效果结算，不触发原版爆炸破坏。玩家使用这些火球牌时也会看到火球投射物，但不会被强制套用烈焰人或恶魂姿态。
  - 代码实现：`BattleVisualEvent.AnimationType` 末尾追加 `BLAZE_FIREBALL` 和 `GHAST_FIREBALL`，保持旧网络 ordinal 顺序。`BattleState.rangedAnimationType(...)` 按卡牌 id 选择小火球或大火球动画，`rangedPrepareTicks(...)` 统一给火球 20 tick 准备时间；`ClientBattleState` 把两种火球接入投射物队列和准备计时，`BattleWorldOverlay` 分别用 `SmallFireball` 与 `LargeFireball` 作为只渲染实体；`ClientEvents` 在准备期间只对烈焰人调用原版 `setCharged(true)`、只对恶魂调用原版 `setCharging(true)`，结束后恢复原状态，并在命中点播放本地粒子和音效。
  - 变更记录：新增怪物专属火球视觉，不新增原版火球实体伤害、点火或地形爆炸规则。

## 本次末影螨专属卡组补充

- 玩法描述：末影螨默认使用双卡循环专属卡组，维护入口见 `docs/monster_decks.md`。它不再使用动态 fallback 卡组，而是只会在战斗中使用 `末影啮咬` 和 `裂隙疾窜`：前者造成 3 点攻击伤害并施加 1 层凝视，后者提供 2 点格挡和 1 层相位。整体定位是持续小额施压、靠相位周旋的高频骚扰怪物；玩家之后获得这些牌时，仍按普通卡牌效果结算。
  - 代码实现：`MonsterDeckProfile` 为 `EntityType.ENDERMITE` 返回固定的 `ENDERMITE_DEFAULT_DECK`，卡组只包含 `builtin_monster_ender_nip` x5 和 `builtin_monster_rift_skitter` x5，未覆盖奖励池会按这两张牌去重生成。`MoonSpireCardRegistry` 注册两张对应内置怪物牌，其中 `builtin_monster_ender_nip` 使用基础攻击加 `GAZE` 效果形成先伤害后挂压，`builtin_monster_rift_skitter` 通过 `BLOCK` 与 `PHASE` 组合承担防守周旋职责；`defaultDeckCardIds(...)` 同步让开发者中心显示同一套默认卡组。
  - 变更记录：为末影螨补齐专属默认卡组与双语名称，不再复用通用 fallback 牌组。
