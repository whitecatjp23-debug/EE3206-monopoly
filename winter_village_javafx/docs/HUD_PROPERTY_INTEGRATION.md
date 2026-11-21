# HUD 與地產模型整合問題

## 問題摘要

目前系統存在兩份地產狀態與規則：

```text
monopoly.property.Property
 oopgroup.WinterVillagePlayerHUD.PropertyData
```

`WinterVillagePlayerHUD` 會使用 `BoardFactory` 和 `BoardPropertyInfo` 取得棋盤地產的初始資料，但之後會將資料複製成自己的 `PropertyData`。HUD 再使用這份複製資料處理持有人、租金、建築、抵押、買賣與交易。

因此，規則層的 `Property` 與 UI 層的 `PropertyData` 可能在以下狀態上不同步：

- 地產持有人
- 建築等級
- 抵押狀態
- 租金計算結果
- 玩家持有地產清單

## 目前架構

```text
BoardFactory
    ↓
monopoly.property.Property
    ↓ BoardPropertyInfo（初始資料快照）
WinterVillagePlayerHUD.PropertyData
    ↓
HUD 自己處理買地、租金、建築、抵押與交易
```

這使得地產規則被實作兩次，也可能導致 HUD 顯示的結果與規則層實際狀態不一致。

## 建議方向

`PropertyData` 應只保留為 UI 顯示 adapter，不應再成為第二套地產模型。地產的唯一規則與狀態來源應改為：

- `monopoly.property.Property`
- `monopoly.property.PropertyMarket`
- `monopoly.property.PropertyDevelopment`
- `monopoly.property.PropertyGroup`
- `monopoly.board.Board`

HUD 應透過 adapter 讀取上述模型的狀態，並將使用者操作委派給規則層，而不是自行重新計算或修改地產狀態。

## 注意事項

這項工作需要整合 `oopgroup` 與 `monopoly` 兩層，可能涉及玩家型別、JavaFX property binding，以及 UI callback 的調整。完成前，應避免讓 HUD 與 `monopoly.property` 同時作為地產狀態的權威來源。
