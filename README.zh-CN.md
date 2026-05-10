# Moon Spire

[English](README.md) | [简体中文](README.zh-CN.md)

Moon Spire 是一个基于 NeoForge 的 Minecraft 1.21.1 模组。

## 模组简介

Moon Spire 为 Minecraft 添加了一套牌组构筑式卡牌战斗玩法。玩家可以挑战生物，消耗费用打出卡牌，管理抽牌堆、弃牌堆和被消耗的牌，并通过制卡台把装备转化为可使用的卡牌。模组还包含状态效果、怪物意图、战斗动画反馈，以及用于编辑卡牌、卡面、怪物卡组和战斗 UI 布局的开发者工具。

## 开源协议

Moon Spire 使用 [Apache License 2.0](LICENSE) 开源。

中文参考说明见 [LICENSE.zh-CN.md](LICENSE.zh-CN.md)。如中文参考说明与英文协议原文存在差异，以英文 [LICENSE](LICENSE) 为准。

## 开发

环境要求：

- JDK 21
- IntelliJ IDEA 或其他支持 Gradle 的 Java IDE

常用命令：

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat build
```

构建后的模组 jar 会输出到 `build/libs/`。

## 资源

- NeoForge 文档：https://docs.neoforged.net/
- NeoForge Discord：https://discord.neoforged.net/
