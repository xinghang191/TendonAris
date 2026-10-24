# Tendon_Aris（Minecraft 1.20.1 / Forge）

一个完整可编译的 Forge 1.20.1 模组，新增中立生物 **Tendon_Aris**：可交易、可用石头招募跟随、被玩家攻击后反击，并拥有专属刷怪蛋、自然生成和石头掉落。

- Minecraft：**1.20.1**
- Forge：**47.4.10**（`mods.toml` 允许 47.x）
- Java：**17**
- Mod ID：`tendon_aris`

---

## 1. 构建与安装

### 1.1 环境

必须使用 **JDK 17**。Gradle Wrapper 已包含在项目中（Gradle 8.8），不需要单独安装 Gradle。

```bat
cd TendonAris
set JAVA_HOME=C:\path\to\jdk-17
gradlew.bat build
```

Linux / macOS：

```bash
cd TendonAris
export JAVA_HOME=/path/to/jdk-17
./gradlew build
```

构建产物：

```text
build/libs/tendon_aris-1.0.0.jar
```

### 1.2 开发环境运行

```bat
gradlew.bat runClient
gradlew.bat runServer
```

`runServer` 第一次启动会生成 `run/server.properties` 和 `run/eula.txt`；将 `eula.txt` 中的 `eula=false` 改为 `eula=true` 后再次启动即可。

### 1.3 安装到游戏

1. 安装 Minecraft 1.20.1 对应的 Forge **47.4.10 或更高的 47.x**。
2. 把 `build/libs/tendon_aris-1.0.0.jar` 放入客户端或服务端的 `mods/` 目录。
3. 客户端和服务端都必须安装该模组（生物、交易和生成逻辑是双端内容）。
4. 使用 Java 17 启动游戏/服务端。

---

## 2. 项目目录

```text
TendonAris/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── README.md
└── src/main/
    ├── java/com/example/tendonaris/
    │   ├── TendonArisMod.java                 模组入口
    │   ├── registry/
    │   │   ├── ModEntities.java               实体类型注册
    │   │   └── ModItems.java                  刷怪蛋注册
    │   ├── entity/
    │   │   ├── TendonArisEntity.java          生物主体、交易、愤怒、掉落
    │   │   ├── TendonArisTrades.java          交易数量常量与报价
    │   │   └── goal/
    │   │       ├── TendonArisAttackGoal.java  按 ATTACK_SPEED 计算冷却的反击 AI
    │   │       └── FollowPlayerGoal.java      石头招募后的跟随 AI
    │   ├── event/ModEvents.java               属性、生成放置、创造模式物品栏
    │   └── client/
    │       ├── ClientSetup.java               仅客户端注册（Dist.CLIENT）
    │       ├── TendonArisModel.java           slim 人形模型
    │       └── TendonArisRenderer.java        实体渲染器
    └── resources/
        ├── META-INF/mods.toml
        ├── pack.mcmeta
        ├── assets/tendon_aris/
        │   ├── lang/en_us.json
        │   ├── lang/zh_cn.json
        │   ├── models/item/tendon_aris_spawn_egg.json
        │   └── textures/entity/tendon_aris.png     Exia 皮肤（64×64 slim）
        └── data/tendon_aris/
            ├── forge/biome_modifier/add_spawn.json  自然生成配置
            └── loot_tables/entities/tendon_aris.json
```

---

## 3. 玩法行为

### 3.1 中立与交易

- 默认没有攻击目标，不会主动攻击玩家。
- 未处于敌对状态时，玩家空手或手持任意物品（石头除外）右键，会打开 **原版商人交易界面**。
- 默认交易：

  | 玩家支付 | 获得 |
  |---|---|
  | 1 个下界合金锭 | 1 个石头 |

- 交易不是“只做外观”的界面：使用 `Merchant` + `MerchantMenu` 实现，只有玩家真正把产物从输出槽取走时才会扣除下界合金锭并给石头；服务端负责校验、客户端由原版 `ClientboundMerchantOffersPacket` 同步。
- 交易距离限制为 **8 格**。超过距离、死亡、切换维度或生物死亡时，`getTradingPlayer()` 返回 `null`，`MerchantMenu.stillValid()` 失败，服务端会自动关闭界面。
- 同一时间只允许一个玩家与该生物交易；第二个玩家打开时会接管交易状态。
- 交易报价最多 9999 次（实际等同无限），价格倍率为 0，不会因为交易次数产生价格浮动。

### 3.2 石头招募跟随

- 主手或副手持有 **石头** 右键 Tendon_Aris，会将它当前跟随目标设为该玩家。
- 每次手持石头右键时，只向该玩家发送一次聊天栏消息（交互事件触发，不会在每 tick 的 AI 循环里重复）：

  ```text
  看看
  ```

  消息使用 `ServerPlayer#displayClientMessage`，不会被广播到全服，也不会每个游戏刻重复发送。
- 石头不会被消耗。
- 跟随 AI 在距离玩家超过 4 格时开始移动，进入 3 格内停止，带滞后区间，避免每刻启停。
- 玩家退出、切换维度、生物被攻击进入敌对状态时，跟随目标会被清除。

### 3.3 被攻击后的敌对与反击

- 玩家（包括玩家发射的投射物）对它造成有效伤害后：
  - 记录攻击者 UUID；
  - 进入敌对状态 60 秒（`ANGER_DURATION_TICKS`，每次再次被攻击会重置）；
  - 将攻击者设为当前目标并开始追击；
  - 清除跟随目标，关闭当前交易界面。
- 敌对期间右键不会打开交易界面，也不会被石头招募。
- 反击伤害为 **1 点生命值**（半颗心），由 `Attributes.ATTACK_DAMAGE = 1.0` 实现。
- 攻击冷却：
  - 1.20.1 玩家的攻击速度以“每秒攻击次数”表示，剑的默认 `ATTACK_SPEED` 为 `1.6`，完整蓄力时间约为 `20 / 1.6 = 12.5` tick。
  - 本模组把同一个属性用在生物上，默认把 `ATTACK_SPEED` 提升到 **2.5**，并由 `TendonArisAttackGoal` 计算：

    ```text
    ceil(20 / ATTACK_SPEED) = ceil(20 / 2.5) = 8 tick = 0.4 秒
    ```

  - 因此它的出手速度明显快于玩家使用剑时的正常近战节奏，但仍处于正常近战范围内；不是原版 `MeleeAttackGoal` 固定写死的 20 tick。
- 只有 `doHurtTarget` 真正返回命中成功时，才向被命中的玩家发送：

  ```text
  有无mc
  ```

  每次成功命中恰好一次，未命中/被无敌帧挡掉不会发送。
- 敌对状态结束后清除目标并恢复中立，可再次交易或招募。

### 3.4 自然生成

- 使用 Forge 1.20.1 的 **Biome Modifier** 数据驱动生成：
  `data/tendon_aris/forge/biome_modifier/add_spawn.json`
- 默认对 `#minecraft:is_overworld` 中的所有主世界群系生效，权重和群体大小与牛一致：

  ```json
  {
    "type": "forge:add_spawns",
    "biomes": "#minecraft:is_overworld",
    "spawners": [
      {
        "type": "tendon_aris:tendon_aris",
        "weight": 8,
        "minCount": 4,
        "maxCount": 4
      }
    ]
  }
  ```

- 生物分类为 `MobCategory.CREATURE`，使用与牛相同的生成上限，且 `removeWhenFarAway` 返回 `false`（牛式持久化）。
- 生成放置通过 `SpawnPlacementRegisterEvent` 注册为 `ON_GROUND` + `MOTION_BLOCKING_NO_LEAVES`。
- 自然生成条件（`TendonArisEntity#checkTendonArisSpawnRules`）：
  - 脚下是坚固可站立方块；
  - 当前位置和上方各一格为空（身高 1.8 格）；
  - 光照等级 > 8；
  - 刷怪蛋、发射器和刷怪笼绕过这些条件。
- 这样可以在沙漠、雪原、蘑菇岛等没有草方块的群系也生成，满足“任何群系”的要求；海洋等液体环境不会生成。

### 3.5 刷怪蛋与掉落

- 刷怪蛋注册 ID：`tendon_aris:tendon_aris_spawn_egg`，位于原版“刷怪蛋”创造模式标签页。
- 刷怪蛋使用 `ForgeSpawnEggItem`，无需额外贴图；颜色为占位的浅骨色 + 暗红色。
- 击杀掉落：
  - 1–3 个石头；
  - `minecraft:looting_enchant`：每级抢夺额外 0–1 个石头；
  - 例如抢夺 III 时，额外 0–3 个，总范围 1–6 个。

---

## 4. 可调整参数

| 参数 | 默认值 | 位置 |
|---|---:|---|
| 下界合金锭消耗 | 1 | `entity/TendonArisTrades.java` → `NETHERITE_COST` |
| 石头产出 | 1 | `entity/TendonArisTrades.java` → `STONE_REWARD` |
| 交易最大次数 | 9999 | `entity/TendonArisTrades.java` → `MAX_USES` |
| 交易经验 | 0 | `entity/TendonArisTrades.java` → `TRADE_XP` |
| 价格浮动倍率 | 0.0 | `entity/TendonArisTrades.java` → `PRICE_MULTIPLIER` |
| 交易距离 | 8 格 | `entity/TendonArisEntity.java` → `TRADE_DISTANCE` |
| 愤怒持续时间 | 1200 tick（60 秒） | `entity/TendonArisEntity.java` → `ANGER_DURATION_TICKS` |
| 反击伤害 | 1.0 | `entity/TendonArisEntity.java` → `createAttributes()` 的 `ATTACK_DAMAGE` |
| 攻击速度/冷却 | 2.5 次/秒 → 8 tick（0.4 秒） | `entity/TendonArisEntity.java` → `ATTACK_SPEED` 常量 |
| 移动速度 | 0.25 | `entity/TendonArisEntity.java` → `MOVEMENT_SPEED` |
| 最大生命 | 20 | `entity/TendonArisEntity.java` → `MAX_HEALTH` |
| 跟随速度 | 1.0 | `entity/TendonArisEntity.java` → `new FollowPlayerGoal(this, 1.0D)` |
| 开始/停止跟随距离 | 4 格 / 3 格 | `entity/goal/FollowPlayerGoal.java` |
| 生成权重 | 8（与牛相同） | `resources/data/tendon_aris/forge/biome_modifier/add_spawn.json` |
| 群体大小 | 4–4（与牛相同） | 同上 `minCount` / `maxCount` |
| 生成群系 | `#minecraft:is_overworld` | 同上 `biomes` |
| 自然生成光照 | > 8 | `entity/TendonArisEntity.java` → `checkTendonArisSpawnRules` |
| 石头基础掉落 | 1–3 | `resources/data/tendon_aris/loot_tables/entities/tendon_aris.json` |
| 抢夺额外掉落 | 0–1 / 级 | 同上 `looting_enchant` |
| 聊天文本 | `看看` / `有无mc` | `entity/TendonArisEntity.java` → `startFollowing` / `doHurtTarget` |

### 调整生成的例子

想改成只在平原和森林生成，把 `biomes` 改成：

```json
"biomes": ["minecraft:plains", "minecraft:forest"]
```

想提高出现频率，把 `weight` 从 `8` 改成 `20`；想每次只生成 1–2 只，把 `minCount` / `maxCount` 改为 `1` / `2`。

想同时在下界/末地生成，可另建 JSON，将 `biomes` 分别改为 `#minecraft:is_nether` 或 `#minecraft:is_end`，其余字段相同。生成放置仍要求坚固地面，因此下界岩浆海和虚空不会生成。

---

## 5. 资源替换

### 5.1 实体贴图

当前贴图使用桌面文件 `Nikke 艾可希雅 (Exia).png`，已复制到：

```text
src/main/resources/assets/tendon_aris/textures/entity/tendon_aris.png
```

这是一个 64×64 的 Minecraft **slim（3 像素手臂）**皮肤。模型已按 slim 布局调整，并显式使用左右手/腿各自的皮肤区域：

- 头部：`(0, 0)`
- 身体：`(16, 16)`
- 右臂：`(40, 16)`（3×12×4）
- 右腿：`(0, 16)`
- 左臂：`(32, 48)`（3×12×4）
- 左腿：`(16, 48)`

模型渲染的是皮肤的基础层，没有额外绘制帽子/外套等 overlay 层；如需完整还原头饰和衣服的立体细节，可在 `TendonArisModel` 中继续添加 `CubeDeformation` 子部件。

替换贴图时保持文件名和 64×64 尺寸不变；如果换成 classic（4 像素手臂）皮肤，需要把手臂宽度改回 `4.0F`。

### 5.2 模型

模型在：

```text
src/main/java/com/example/tendonaris/client/TendonArisModel.java
```

当前模型为 64×64 slim 人形基础层。如果要换成其他比例，修改 `createBodyLayer()` 中的 `addBox` 参数和 `PartPose` 偏移，同时调整 UV；贴图尺寸不变时可继续使用 64×64。

### 5.3 刷怪蛋

刷怪蛋颜色在：

```text
src/main/java/com/example/tendonaris/registry/ModItems.java
```

`0xD8C7B0` 是底色，`0x8E2F2F` 是斑点色。刷怪蛋物品模型使用原版模板 `minecraft:item/template_spawn_egg`，不需要额外贴图。

---

## 6. 服务端/多人安全说明

- `client` 包内的类全部只在 `Dist.CLIENT` 下加载，通过 `ClientSetup` 的 `@Mod.EventBusSubscriber(value = Dist.CLIENT)` 注册模型和渲染器；专用服务器不会加载这些类。
- 实体、交易、生成、AI 和聊天消息全部在服务端执行。
- 聊天消息只通过 `ServerPlayer#displayClientMessage` 发给单个玩家，没有使用 `ServerLevel#broadcastEntityEvent` 或全服广播。
- 交易使用原版 `MerchantMenu` 的服务端校验和同步机制；客户端不能直接修改服务端的交易结果。
- 刷怪蛋、掉落表和群系生成都使用服务端数据包资源，不需要客户端额外代码。

---

## 7. 默认取舍说明

需求中没有明确规定的数值，采用以下合理默认值：

- 1 个下界合金锭 → 1 个石头；
- 9999 次交易上限（实际无限）；
- 被攻击后愤怒 60 秒；
- 攻击冷却 8 tick（0.4 秒），对应 2.5 攻击速度（比剑的 1.6 更快）；
- 自然生成 4 只一组、权重 8，与牛一致；
- 石头掉落 1–3 个，抢夺每级额外 0–1 个；
- 石头招募不消耗石头，每次右键都会回复 `看看`；
- 敌对状态下拒绝交易和招募。

以上数值均可按第 4 节直接修改。

---

## 8. 验证建议

1. `gradlew.bat build` 应生成 `build/libs/tendon_aris-1.0.0.jar`。
2. `gradlew.bat runServer` 启动开发服务器，日志中不应出现注册错误或缺失数据包错误。
3. 进入游戏后：
   - `/give @s tendon_aris:tendon_aris_spawn_egg` 获取刷怪蛋；
   - 放置后空手右键，确认打开交易界面并能用下界合金锭换到石头；
   - 手持石头右键，确认聊天栏只出现一次 `看看` 并开始跟随；
   - 攻击生物，确认其反击造成半颗心伤害，且每次命中只出现一次 `有无mc`；
   - 击杀后确认掉落石头，附魔抢夺后数量增加；
   - 用 `/summon tendon_aris:tendon_aris` 验证实体命令可用。

## Credits

Author and creator: **Heyun1018**.
