# Monopoly Code Layers

三个并行层，当前编译可过，但 `monopoly` 层尚未被任何代码实现。

| Layer | Package | Status | Entry point |
|---|---|---|---|
| Domain model (S1) | `monopoly` | Interfaces + exceptions only | `monopoly.TestMain`（冒烟测试） |
| HUD + game rules (S4/S6) | `oopgroup` | Runnable `Application`，主入口 | `oopgroup.WinterVillagePlayerHUD` |
| 3D scene (S5) | `wintervillage` | 由 HUD 调 `createBoard3D()` 嵌入 | `wintervillage.WinterVillageApp`（仅独立调试） |

## 依赖方向
- `oopgroup` → `wintervillage`（在 `startMatchBtn` 的 onAction 里调 `createBoard3D()` 和 `new WinterVillageBridge(...)`）
- `wintervillage` → `oopgroup`（bridge 实现 HUD 的 `WinterVillageUIBridge`）
- `monopoly` 独立，无依赖

包级循环依赖 `oopgroup ↔ wintervillage` 是**有意为之**的桥接层；不要在 static 初始化块里互相 new。

## Run
- HUD（主入口）：`cd winter_village_javafx && ./run_hud.sh`
- 3D 独立调试：`cd winter_village_javafx && ./run.sh`（或 IntelliJ 里跑 `wintervillage.WinterVillageApp`）

## Roadmap
1. 让 `oopgroup.Player` 实现 `monopoly.Player`
2. 让 `PropertyData` 实现 `monopoly.Buyable`
3. 把 HUD 的 `InsufficientFundsException` 换成 `monopoly` 异常层级
4. 写 `MonopolyGame` 引擎，通过 `GameObserver` 同时驱动 HUD 和 3D 视图