# bbs-posecurve-addon 兼容规划（V1.7.22-Alpha）

- 编写：2026-11-22
- 基线：BCP **V1.7.21-Alpha**
- 对象：`Addon\bbs-posecurve-addon-3.1.2.jar`（58 个类，包 `bbsplus.example.bbsplus`）
- 官方 Wiki：<https://www.yuque.com/mhmzh/bbs-wiki/ebl2cuwld5gbzx5i#A8WWA>（抓取被反爬拦截，语义以反编译为准）
- 状态：**仅规划，未改代码、未改版本号**

---

## 一、已确认口径（本次录入）

1. **曲线主标量跟随 UI 选中轴**：游动-绝对 / 追踪-相对 / 追踪-绝对 三选一，切哪个轴，曲线写的就是哪个轴。
2. **只在装载 posecurve 时启用**：未装 `bbs-posecurve-addon` 时兼容内容一律不生效，行为与 V1.7.21 一致。
3. **三轴同屏、三色**，X / Y / Z 三条轨道的关键帧对外都表现为三个轴：

| 轴号 | 名称 | 字段 | 颜色 | 联动 |
| --- | --- | --- | --- | --- |
| 0 | 游动-绝对 | `roamValue` | `#FFFF4040` | 独立，不参与换算 |
| 1 | 追踪-相对 | `trackRelative` | `#FF00FFFF` | 写 1 → 重算 2 |
| 2 | 追踪-绝对 | `trackAbsolute` | `#FF4000FF` | 写 2 → 重算 1 |

4. **追踪-相对 ⇄ 追踪-绝对 数值互相同步与换算**：写任一条，另一条立即按锚点重算；模式位（`roamMode` / `trackMode`）不动。

---

## 二、结论摘要

贸然兼容的两个真问题：

- **类型污染（致命）**：posecurve 写值统一走 `keyframe.setValue(factory.yToValue(v))`。我们没覆写 `yToValue`，接口默认返回 `Double` → XyzFrameData 关键帧被换成 Double，后续任何 `(XyzFrameData)` 强转直接 CCE，存档写出坏类型。自动手柄、自动向量、拖拽都能触发。
- **曲线失真**：读值走 `factory.getY(value)`，默认 `0.0` → 三轴全压成 0 平线，Y 轴自适应退化。

播放端是**安全**的：`MixinKeyframeSegment` 只在 `PoseTransformKeyframeFactory` / `TransformKeyframeFactory` 上做贝塞尔，我们的工厂不在名单里；`MixinUIKeyframeFactorySync` 有双重 `instanceof` 守卫。

本次新查出两条硬约束，直接决定方案形态：

- **posecurve 的轴数是写死的**：`bbsplus$getAxisCount(kf)` = `value instanceof Vector3f ? 3 : 1`（字节码已核）。自定义类型永远只出 1 轴，**共享图上不可能直接得到三轴**。
- **posecurve 的轴颜色只有一份**：颜色取自 `UIKeyframeSheet.color`，`bbsplus$handleColor` 只做压暗 + 透明度（`Colors.mulRGB(0.68f)` + `setA(min(a,0.72f))`）。**三色也不可能由它给出**。

→ 三轴三色只能由 BCP 自己画；而 `UIKeyframes.editSheet(sheet)` 对每条 sheet 都是 `new UIKeyframeGraph(this, sheet)`（字节码已核），「换图」入口存在但成本高（约 600~900 行）。

**方案定型：单轴驱动 + 双轴叠绘**（不改 posecurve 任何代码、不抢它的注入点）

- 活动轴（UI 选中的那条）交给 posecurve 原生曲线图：`getY` 返回该轴值 → 有真实曲线、Y 轴自适应正常、拖拽/自动工具只改这一轴。
- 另外两条轴由我们在同一块图区**只读叠绘**，各自用指定颜色 → 三轴同屏、三色齐全。
- 写值永远走我们自己的 `yToValue`：复制模板、只换目标轴字段、**绝不产生 Double**；轴 1/2 写入后调换算器同步另一轴。
- 未装 posecurve：整段兼容关闭，`getY` 维持旧行为。

本期明确取舍：非活动轴不能在图上拖（切轴即可编辑）；换轴时 Y 轴视图会重拟合。要「三轴同时可拖 + 各自贝塞尔手柄」需自建完整曲线图，记入 V1.8 备选。

> **2026-11-23 修订**：本节「单轴 + 叠绘」方案**已被第九章（三子窗曲线编辑）取代**。下面是当时的推理留档，实现一律以第九章为准。

---

## 三、反编译事实清单（含本次补充）

| posecurve 组件 | 注入点 / 机制 | 对我们的影响 |
| --- | --- | --- |
| `MixinKeyframeMultiHandles` → `Keyframe` | 给所有关键帧加 `lx/ly/rx/ry` + 多分量手柄列表，参与序列化 | 无害，只多一份手柄数据，类型不变 |
| `MixinKeyframeSegment#createInterpolated` | 仅 PoseTransform / Transform 工厂做贝塞尔 | **不影响播放端** |
| `MixinUIKeyframeFactorySync#update` | 仅 `UITransform` / `UIPoseTransform` 工厂时同步面板 | 不影响 `UIXyzKeyframeFactory` |
| `MixinUIKeyframeGraph` → `UIKeyframeGraph` | 全局图，接管 render / grid / 鼠标 / 手柄 / 轨道列表列 | **主战场**，XYZ sheet 也在这张图上 |
| `bbsplus$getAxisCount` | `Vector3f → 3`，其余 `→ 1` | 我们的类型恒 1 轴（**硬约束**） |
| `bbsplus$getValue(kf, axis)` | `Vector3f` 取分量，否则 `factory.getY(value)` | 默认 0.0 → 0 平线 |
| `bbsplus$setValue(kf, axis, v)` | `Vector3f` 克隆改分量，否则 `setValue(factory.yToValue(v))` | **默认返回 Double → 类型污染** |
| `bbsplus$handleColor(int)` | `Colors.mulRGB(0.68f)` + `setA(min(a,0.72f))` | 只压暗，**不提供分轴配色** |
| `bbsplus$isCurveEditable(sheet)` | 仅 `isNumeric` / POSE_TRANSFORM / TRANSFORM | 我们天然进不了它的曲线编辑 sheet |
| `bbsplus$skipAutoHandleTools` | 仅 `UIPoseTransformKeyframeGraph` 或 `sheet == null` 跳过 | 普通图上自动手柄按钮可用 → 触达写值 |
| `bbsplus$applyAutoVector` | 经 `getValue` / `setValue` 逐分量套用 | 同写值路径，可污染 |
| `bbsplus$reserveTrackListColumn` / `renderTrackList` | 图上让出「轨道列表」列并临时改 `graphArea` 再还原 | 叠绘必须用**同帧实际 `graphArea`**，否则整段错位 |
| `bbsplus$collectValues` / `fitVertical` | 用 `getValue` 算范围 → `yAxis.view(...)` | 全 0 时视图退化 |

BBS 侧（本次核实）：

- `IKeyframeFactory` 只有 `getY` / `yToValue` 两个标量钩子，无多分量接口。
- `UIKeyframeSheet.color` 是 public int → 活动轴换色可行。
- `UIKeyframes.editSheet(sheet)` 对每条 sheet `new UIKeyframeGraph(this, sheet)`；`currentGraph` 是 `IUIKeyframeGraph`。
- `UIKeyframeGraph` 提供 `toGraphY` / `fromGraphY`，`graphArea` 在 `UIKeyframes` 上 → 叠绘可直接复用坐标换算。

---

## 四、方案设计

### 4.1 单轴驱动（活动轴 = 曲线主标量）

改 `XyzFrameDataKeyframeFactory`：

- `static XyzAxis activeAxis`，默认 `ROAM_ABSOLUTE`，由剪辑面板切换
- `static XyzFrameData lastTemplate`，`getY` 时缓存，供 `yToValue` 复制
- `getY(v)`：**仅兼容开关打开时**返回 `activeAxis` 对应字段；否则返回 `0.0`（保持 V1.7.21 行为）
- `yToValue(y)`：`lastTemplate == null ? createEmpty() : copy 后只写 activeAxis 字段`
- **`yToValue` 绝不返回非 XyzFrameData 的部分始终生效**（属缺陷修复，不受开关控制）
- 切轴时把当前 `UIKeyframeSheet.color` 改成该轴颜色 → 图上活动曲线颜色始终正确

### 4.2 双轴叠绘（非活动轴，只读）

- 在 `UIMultiTrackerClip` 的曲线面板挂绘制钩子（`renderTopmostKeyframes` 时机），只读绘制另外两条轴：
  - X：`keyframes.toGraphX(tick)`，端点规则同 BBS
  - Y：`graph.toGraphY(该轴字段值)`
  - 颜色：轴 1 `#FF00FFFF`、轴 2 `#FF4000FF`、轴 0 `#FFFF4040`；非活动轴降透明度（约 0.6）以示只读
- 必须在**同一帧**读取 `keyframes.graphArea`（posecurve 临时 shift 再 restore，取错时机会整段左移）
- 非活动轴不可点、不可拖：命中判定只认活动轴，避免一帧内多点写

### 4.3 追踪-相对 ⇄ 追踪-绝对 同步

新增 `client/curve/XyzTrackSync`：

- `install(clip, tickResolver)`：`UIMultiTrackerClip` 打开剪辑时安装，提供「某 tick → 锚点世界位置 + 相位」
- `apply(frame, axis, tick)`：轴 1 → 由锚点算 `trackAbsolute`；轴 2 → 由锚点算 `trackRelative`；算法沿用剪辑现有换算代码，不新造公式
- **上下文缺失时拒写**：返回 false、保留原值、记一条 debug 日志。宁可这次拖拽不生效，也不写脏数据
- tick 来源：`yToValue` 只拿 `double`，所以由工厂在 `getY`（渲染/命中前必先读值）时记录 `lastKeyframeTick`，与 `lastTemplate` 同源成对

### 4.4 兼容开关

- `BcpPosecurveCompat.enabled()` = `FabricLoader.isModLoaded("bbs-posecurve-addon")` && 配置项 `posecurve_compat`（默认 true，写进 `config/bbs/settings/conflict_features.json`）
- 关闭或未装：`getY` 走旧值、不叠绘、不改 sheet 颜色、轴选择控件隐藏
- 只做只读探测：不加 mixin 到 posecurve 的类，不改 `editSheet`，不碰它的数据结构

---

## 五、实施步骤（V1.7.22-Alpha）

| # | 落点 | 内容 | 预估 |
| --- | --- | --- | --- |
| 1 | `data/XyzFrameDataKeyframeFactory.java` | `getY` / `yToValue` / `activeAxis` / `lastTemplate` / `lastKeyframeTick` | 60 行 |
| 2 | 新增 `client/curve/XyzAxis.java`、`XyzTrackSync.java` | 轴枚举、换算服务、上下文缺失拒写 | 90 行 |
| 3 | `client/ui/UIXyzKeyframeFactory.java` | 切轴同步 `sheet.color`、触发重绘 | 30 行 |
| 4 | `client/ui/UIMultiTrackerClip.java` | 三轴选择控件 + 安装 `XyzTrackSync` | 80 行 |
| 5 | 新增 `client/curve/XyzAxisOverlay.java` | 非活动轴只读叠绘（两色两线） | 100 行 |
| 6 | `client/BcpConflictDetection.java` | 探测 posecurve、写开关、启动日志一行 | 30 行 |

合计约 350~400 行，全部为 BCP 自有代码，零跨 mod 注入。播放端（`applyClip` / `AnchorSampler` / `linearFdAt`）不动。

---

## 六、风险与降级

| 风险 | 说明 | 降级 |
| --- | --- | --- |
| 叠绘错位 | posecurve 中途 shift `graphArea` | 同帧取 `graphArea`；仍错则关叠绘，降为单轴 |
| 换轴视图跳变 | `collectValues` 只算活动轴 | 换轴后保留 `rememberView`，允许手动 fit |
| 换算上下文缺失 | 在非剪辑面板打开图 | 拒写 + 日志，值不变 |
| 自动工具只作用活动轴 | `AutoVectorHandles` 按 1 轴处理 | 本期接受，文档写明「自动工具只作用当前轴」 |
| 手柄数据冗余 | `MixinKeyframeMultiHandles` 每帧多存 4 个标量 | 无害，体积可忽略 |

**本期不做**：三轴同时可拖、每轴独立贝塞尔手柄、让 posecurve 认我们的类型为 3 轴、自建完整 `UIXyzKeyframeGraph`。四项归 V1.8 备选（需替换 `editSheet` 建图逻辑，约 600~900 行）。

---

## 七、验收清单

1. 未装 posecurve：三轴控件与叠绘不出现，行为与 V1.7.21-Alpha 逐项一致。
2. 装 posecurve：打开多样追踪剪辑 → X/Y/Z 轨出现三条不同颜色曲线（红 / 青 / 紫），活动轴颜色与 sheet 颜色一致。
3. 拖关键帧 → 只有活动轴值变化；松手后重开属性面板，5 个字段仍在（不出现 Double、不丢模式位）。
4. 轴 1（追踪-相对）拖到 5.36 → 轴 2（追踪-绝对）同步变成同一物理位置的对应数值；反向同样成立。
5. 轴 0（游动-绝对）编辑 → 追踪组两个数值不变（独立）。
6. 多选 XYZ 关键帧点自动手柄 / 自动向量 → 只改活动轴；保存、退出、重进客户端可正常读档、播放不崩。
7. 老存档（`roam_mode = 0` 的 V1.5.4 时代帧）读档后画面零偏移。

---

## 八、仍需你确认（3 条）

1. **非活动轴只读**是否接受？（要三轴同时可拖就得走 V1.8 自建图）
2. **换算取值时刻**：轴 1/2 同步时，锚点按「该关键帧 tick 所在时刻」算，还是按「当前播放刻」算？（默认按关键帧 tick）
3. **换轴是否重拟合 Y 轴**：默认重拟合（曲线看得清）；若更看重视图稳定，就保留上次视图。

---

## 九、定稿方向：三子窗曲线编辑（2026-11-23 追加）

依据图 1（回放编辑器 X 轨道的贝塞尔曲线编辑画面）与图 2（三子窗构思草图）改写：曲线区纵向切成三个**独立的编辑子窗口**，每个子窗只画一条轴、各自配色与边框、各自 Y 轴刻度与缩放；游动-绝对独立，追踪-相对 ⇄ 追踪-绝对 双向同步。

### 9.1 布局

- 三子窗纵向堆叠，默认等高（约 1:1:1），每个子窗带 2px 彩框 + 左上角标题（游动-绝对 / 追踪-相对 / 追踪-绝对）
- **窗间可拖分隔条**：拖动即调整三窗高度比例，单窗最小高度约 60px；比例持久化（下次打开保持上次布局），双击分隔条回到等高
- 顶部只保留**一条**共享时间标尺；播放头竖线贯穿三窗，保证同一时刻三窗纵向对齐
- 左侧轨道列表不再重复三份，读数区只出现一次
- 每个子窗自带 Y 轴刻度（如 3.5 / 3.0 / 2.5 这种本轴独立刻度），互不共享值域
- 与图 2 一致：三窗之间的分界就是彩框本身，不额外加分隔条

### 9.2 配色（按图 2 草图）

| 子窗 | 轴号 | 边框 / 曲线颜色 | 联动 |
| --- | --- | --- | --- |
| 游动-绝对 | 0（`roamValue`） | `#FFFF4040` 红 | 独立，不触达追踪组 |
| 追踪-相对 | 1（`trackRelative`） | `#FF00FFFF` 浅蓝 | 写 1 → 重算 2 |
| 追踪-绝对 | 2（`trackAbsolute`） | `#FF0040FF` 深蓝 | 写 2 → 重算 1 |

> 轴 2 颜色已按你的选择定为更深的 `#FF0040FF`（草案阶段的 `#FF0080FF` 作废）。

### 9.3 交互规则

- **拖关键帧改值**：只改本子窗对应那一轴；同一关键帧对象，所以 tick 拖动会三窗同步
- **新增 / 删除关键帧**：三窗同步（同一条通道，只是三个视图）
- **选择集共享**：在任一子窗选中关键帧，另两窗同步高亮
- **缩放独立**：每子窗自己一套 `yAxis` 与视图记忆，「间距缩放」互不牵连；X 时间轴共享（否则三窗失去对齐，如果你要 X 也独立请单独说）
- **高度可调**：拖动窗间分隔条改三窗高度比例，三窗都随之重新分配绘制区
- **游动-绝对**：任何编辑都不触发追踪组换算
- **追踪-相对 / 追踪-绝对**：任一侧写入 → `XyzTrackSync` 按该关键帧 tick 的锚点换算另一侧 → 另一子窗曲线立即重绘；换算上下文缺失则**回滚本次拖拽并提示**，不写脏数据

### 9.4 为什么不再走 posecurve 的图

- posecurve 一张图只认一种 sheet 颜色、轴数写死 `Vector3f ? 3 : 1`，天然出不了「三窗三色」
- 三窗要各自独立的 `yAxis` 与视图记忆，就必须是三个独立实例；而 `UIKeyframeGraph` 的绘制区取自共享的 `UIKeyframes.graphArea`，本就不是为纵向堆叠设计的
- 结论：BCP 自建 `XyzTripleCurvePanel`（一个纵向容器）+ 三个 `XyzCurveLane`（单轴曲线道）

### 9.5 实现要点

- 共享层：一份 X 映射（tick ⇄ 像素）、一段按可见区间采样的缓存，三窗同一区间只采一次
- 每 lane：自己的 `Scale yAxis` + 自己的视图记忆（按轴号做键）+ 自己的刻度绘制
- 曲线采样：按像素步进（约 2px）取当前段 `KeyframeSegment` 的插值结果；关键帧点、选中态、悬停读数自绘
- 命中与拖拽：按 lane 分区判定，避免跨窗误触；拖拽只改本轴值
- 渲染顺序：底网格 → 三窗曲线 → 关键帧点 → 播放头 → 读数
- 预计 450~600 行（渲染 + 命中 + 拖拽 + 视图记忆），全部在 BCP 内，零跨 mod 注入
- posecurve 仍可共存：其余轨道照旧用它的图，我们只替换「多样追踪」XYZ 这条通道的编辑视图
- 播放端不依赖本面板，仍走 `factory.interpolate`；只改值则播放端零改动

### 9.6 贝塞尔手柄（已确认：本期走 P1）

- 现状：posecurve 的手柄挂在 `Keyframe` 上，全局一套、不区分轴；三窗共用会互相影响，做不出「各窗独立手柄」
- **P1（本期采用）**：三窗可拖值、可拖 tick、可独立缩放；曲线形态由关键帧的插值与手柄决定，三窗形状一致（不是每轴独立手柄）
- **P2（暂缓）**：在 `XyzFrameData` 增加 3×4 个可选手柄字段（缺省不写、旧档兼容），`interpolate` 按轴独立套用 → 每窗真正独立的手柄与曲线形态；等 P1 手感确认后再评估
- P2 的代价：帧值序列化字段变多、播放端插值要按轴走手柄、老档要保证缺省值等价于当前行为

### 9.7 分阶段

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| P0 | `getY` / `yToValue` 防污染垫片（与界面无关，必做） | 60 行 |
| P1 | `XyzTripleCurvePanel` + 三条 `XyzCurveLane`：三色、独立 Y 缩放、值拖拽、rel⇄abs 同步 | 450~600 行 |
| P2 | 每轴独立贝塞尔手柄（`XyzFrameData` 字段扩展 + 按轴插值） | 150~200 行（暂缓） |

### 9.8 验收

1. 三窗同时可见，红 / 浅蓝 / 深蓝（`#FF0040FF`）的边框与曲线颜色正确，标题正确
2. 三窗各自缩放互不牵连；X 时间轴与播放头严格对齐
3. 拖动窗间分隔条：三窗高度按比例变化、最小高度生效、重开面板保持上次比例，双击回到等高
4. 拖轴 0 → 轴 1 / 轴 2 数值与曲线都不动；拖轴 1 → 轴 2 同步换算，反向亦然
5. 未装 posecurve：整个三子窗面板不出现，行为回到 V1.7.4
6. 存读档：5 个字段完整，不出现 `Double`，模式位不丢
7. 编辑读数与播放一致：同一 tick 的曲线值 = 播放时相机用的值

### 9.9 已定事项（2026-11-23）

1. 子窗手柄：**走 P1**，三窗共用关键帧的插值与手柄；P2（每轴独立手柄）暂缓
2. 轴 2 深蓝：**`#FF0040FF`**
3. 三窗高度：**可拖分隔条**，单窗最小高度约 60px，比例持久化，双击回等高

> 现在 V1.7.22-Alpha 的实施范围 = P0 + P1（约 510~660 行），P2 不进本版。
