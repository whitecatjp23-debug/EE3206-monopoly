# 擁有權整合：`Buyable` 與 `Property` 的介面衝突

**狀態：** ⏸ 待 1號 處理（2號 的部分已就緒，卡在 `Buyable` / `Player` 兩個介面）
**影響範圍：** `monopoly.Buyable`、`monopoly.Player`、`monopoly.property.Property`、`monopoly.property.PropertyMarket`
**相關分工：** 1號 @JoeChanHk（核心邏輯，`Buyable` / `Player`）、2號 @瀨名小雛（棋盤及地產，`Property` / `PropertyMarket`）、6號 @Shironeko（整合與測試）

> **給接手的人：** 你只需要改三個檔案，而且改動極小——`Buyable` 目前 0 個實作、0 個呼叫端。
> 想直接動手請看 §3；想知道為什麼要這樣改請看 §1～§2。
> 2號 的地產程式碼已經準備好，只等你這三個檔案。

---

## 0. 一句話結論

`PropertyMarket.buy()` 沒有把買到的地產登記回玩家，導致 `Player.getOwnedProperties()` 永遠是空的。
這不是「誰的程式有 bug」，而是**兩個介面對不上**：

| 項目 | 現況 | 屬於誰 |
|---|---|---|
| `PropertyMarket.buy()` 少了 `buyer.addProperty(...)` 這一行 | 缺陷的**呼叫點** | 2號（已預留 `TODO`，見 §2.1） |
| `Buyable.calculateRent()` 沒有骰數參數、`purchase()` 又和 `PropertyMarket` 重複 | 缺陷的**根因** | 1號（`@author joe`） |
| `Player` 沒有 `removeProperty(...)` | 賣地／破產時無法反向同步 | 1號（`@author joe`） |

**為什麼 2號 不能自己補這兩行：** `buyer.addProperty(property)` 要求 `property` 是 `Buyable`，
但 `Property` 不是 `Buyable`，**而且不該是**——因為 `Buyable` 現在的樣子沒辦法正確描述地產（見 §2）。
硬讓 `Property` 去 `implements Buyable` 會編不過：`calculateRent()` 與 `calculateRent(int)` 簽章衝突。

**好消息：** `Buyable` 在整個 repo 目前是 **0 個實作、0 個呼叫端**（只有 `Player` 的簽章用到它）。
所以修改它的成本極低，不會弄壞任何現有行為。

---

## 1. 現況：擁有權其實有三份

| 來源 | 誰在維護 | 是否權威 |
|---|---|---|
| `Property.getOwner()` | `AbstractProperty`（`monopoly.property`） | ✅ **規則層唯一真相** |
| `Player.getOwnedProperties()` → `List<Buyable>` | `Player` 的實作 | ❌ 目前永遠空的 |
| `WinterVillagePlayerHUD.Player.ownedProperties` | HUD（`oopgroup`） | ⚠️ 主程式實際在用這份 |

租金、整組加倍、鐵路數量加成、公用事業 ×4／×10、能否建造，**全部都只讀 `Property.getOwner()`**
（見 `PropertyGroup.countOwnedBy()` @ `monopoly/property/PropertyGroup.java:89`）。
也就是說：**規則層完全不需要 `Player.getOwnedProperties()` 也能正常運作**。

真正會出事的，是「同一筆擁有權存在兩份、彼此不同步」——這正是這次把 HUD 的 28 筆地產改成
由 `BoardFactory` 產生時修掉的問題。如果不處理，之後會再遇到一次：

```text
玩家在 HUD 買地 → HUD 的 Player.ownedProperties 有資料
                → monopoly 的 Property.getOwner() 卻沒被設定
兩個系統各自算淨資產、算整組、算破產，結果不一致
```

---

## 2. 為什麼不能「直接讓 `Property` 實作 `Buyable`」

現有的 `Buyable`（`monopoly/Buyable.java:11`）有五個方法，逐一比對 `Property`：

| `Buyable` 的方法 | `Property` 的對應 | 能否直接符合 |
|---|---|---|
| `int getPrice()` | `int getPurchasePrice()` | ⚠️ 名稱不同，但可用 default method 轉接 |
| `int calculateRent()` | `int calculateRent(int diceTotal)` | ❌ **簽章不同，且不是改名就能解決** |
| `Player getOwner()` | `Player getOwner()` | ✅ 完全一致 |
| `boolean isOwned()` | `default boolean isOwned()` | ✅ 完全一致 |
| `void purchase(Player buyer)` | 無（由 `PropertyMarket.buy()` 負責） | ❌ 與 `PropertyMarket` 重複 |

### 2.1 `calculateRent()` 是真正的阻塞點

```java
// UtilityProperty.java —— 公用事業的租金一定要骰數
public int calculateRent(int diceTotal) {
    int owned = getGroup().countOwnedBy(getOwner());
    return diceTotal * (owned >= 2 ? DOUBLE_MULTIPLIER : SINGLE_MULTIPLIER);
}
```

無參數的 `calculateRent()` **在數學上沒辦法表達公用事業的租金**。
若硬塞一個 `int calculateRent() { return calculateRent(0); }`，公用事業的租金會永遠是 $0。
這是唯一一個「不能靠 default method 化解」的衝突。

### 2.2 `purchase()` 會製造第二條購買路徑

官方流程是「先扣款、再轉移所有權、驗證與例外都在同一個地方」，
`PropertyMarket.buy()` @ `monopoly/property/PropertyMarket.java:60` 已經做完了這件事（含 `BankruptcyException`）。
如果 `Buyable.purchase()` 也存在，就會出現兩條可以買地的路徑，
而且 `purchase()` 的 `throws` 沒有 `BankruptcyException`，兩邊的例外契約不一致。

**結論：不要讓 `Property` 去遷就 `Buyable`，而是把 `Buyable` 縮成它真正該有的樣子。**

### 2.3 2號 目前留的 `TODO`

`PropertyMarket.buy()` 已經在該行位置留了註解，等你改完介面再解開：

```java
    property.setOwner(buyer);
    // TODO(擁有權整合)：這裡還缺 buyer.addProperty(property)，
    // 但 Property 目前不是 Buyable（Buyable.calculateRent() 沒有骰數參數，
    // 無法表達公用事業的租金）。詳見 docs/OWNERSHIP_INTEGRATION.md。
    return price;
```

`sellToBank()` 同理，缺 `owner.removeProperty(property)`。

---

## 3. 要你改的部分（1號）

三個檔案、三個改動。改完之後 2號 會解開 §2.3 的 `TODO`，一次收尾。

### 3.1 `monopoly/Buyable.java`

```java
package monopoly;

/**
 * 「可被玩家擁有」的抽象：地產（街道、鐵路、公用事業）都屬於這一類。
 *
 * <p>這個介面刻意只描述狀態與價格，<b>不包含購買流程</b>：
 * 購買牽涉付款、擁有權轉移與破產判定，統一由
 * {@code monopoly.property.PropertyMarket} 處理，
 * 避免同一個動作有兩條實作路徑。
 */
public interface Buyable {
    /** 回傳購買價格。 */
    int getPrice();

    /**
     * 依骰子點數計算應付租金。
     *
     * @param diceTotal 兩顆骰子的點數和（公用事業會用到；街道與鐵路會忽略）
     */
    int calculateRent(int diceTotal);

    /** 目前持有人；無人持有時回傳 null。 */
    Player getOwner();

    /** 是否已被某人持有。 */
    default boolean isOwned() {
        return getOwner() != null;
    }
}
```

三處改動：`calculateRent()` → `calculateRent(int)`、刪掉 `purchase()`、`isOwned()` 改成 default。

### 3.2 `monopoly/Player.java`

```java
    void addProperty(Buyable property);

    /**
     * 移除一筆持有的地產（賣回銀行、破產清算、交易轉手時使用）。
     *
     * @param property 要移除的地產；不在持有清單時忽略
     */
    void removeProperty(Buyable property);

    List<Buyable> getOwnedProperties(); //all the properties of the player.
```

> **為什麼一定要有 `removeProperty`：** `TestPlayer.getOwnedProperties()` 回傳的是
> `List.copyOf(...)`（不可變），`Buyable` 也沒有 `setOwner`，
> 所以外部無法用「改清單」或「改持有人」的方式反向同步。只能由 `Player` 自己提供。

### 3.3 `monopoly/TestMain.java`

匿名 `Player` 實作（約第 48 行）補一行：

```java
            @Override public void removeProperty(Buyable prop) { properties.remove(prop); }
```

### 3.4 改完之後請確認

```bash
cd winter_village_javafx
./build.sh   # 應該要過
./test.sh    # 應該要 793 項通過（數字不變，因為測試還沒解開 TODO）
```

`monopoly.TestMain` 的冒煙測試也要能跑完（`java -cp out/classes monopoly.TestMain`）。
因為 `Buyable` 目前沒有實作者，改介面本身不會影響任何執行期行為。

---

## 4. 2號 接著會做的事（你改完之後）

| 檔案 | 改動 |
|---|---|
| `monopoly/property/Property.java` | `extends Buyable` + `default getPrice()` 轉接 `getPurchasePrice()` |
| `monopoly/property/PropertyMarket.java` | 解開 `buy()` 的 `addProperty`、`sellToBank()` 的 `removeProperty` |
| `monopoly/test/TestPlayer.java` | 補 `removeProperty` 實作 |
| `monopoly/test/PropertyTests.java` | 補 4 個情境（見 §5） |

`Property` 的接法（**既有呼叫端一行都不用改**）：

```java
public interface Property extends Buyable {
  int getPurchasePrice();          // 原本的方法保留

  /** 實作 {@link Buyable#getPrice()}：對地產而言價格就是購買價。 */
  @Override
  default int getPrice() {
    return getPurchasePrice();
  }
  // getOwner() / isOwned() / calculateRent(int) 已經完全符合，不需改動
  ...
}
```

---

## 5. 驗收測試（2號 會補上）

| 情境 | 斷言 |
|---|---|
| 買地後玩家持有清單有地產 | `getOwnedProperties().size() == 1`、內容是該地產 |
| 買不起時清單不變 | 拋 `InsufficientFundsException`，`getOwnedProperties().isEmpty()` |
| 賣回銀行後清單移除 | `getOwnedProperties().isEmpty()` 且 `property.getOwner() == null` |
| 抵押不影響持有清單 | `mortgage()` 後仍在 `getOwnedProperties()` 內 |

**現在 `./test.sh` 是 793 項全過，正是因為沒有任何測試在 `buy()` 之後檢查 `getOwnedProperties()`。**
補上之後會變成 802 項。

---

## 6. 替代方案（如果你沒時間改介面）

**方案 B：承認 `Property.getOwner()` 是唯一真相，`Player.getOwnedProperties()` 定位為 UI 快取。**

做法：`PropertyMarket` 完全不碰 `Player`，改由呼叫端自己維護；
或把 `Player.getOwnedProperties()` 標成 `@Deprecated`，等於放棄這個方法。

| | 方案 A（建議） | 方案 B |
|---|---|---|
| 要動的檔案 | 3 個（`Buyable`、`Player`、`TestMain`），都在 1號 手上 | 0 個 |
| 風險 | 低（`Buyable` 目前 0 實作） | 低，但留下一條死路 |
| 結果 | 擁有權一份真相＋一份快取，自動同步 | `Player.getOwnedProperties()` 永遠是空的 |
| 之後誰會踩到 | 沒人（已同步） | 任何想用 `getOwnedProperties()` 算淨資產／破產的人 |

**只有在完全沒時間時才選 B**，並請在 `Player` 的 Javadoc 寫明「不保證與 `Property.getOwner()` 同步」。

**不建議的方案 C：** 讓 `Property` 硬塞 `int calculateRent() { return calculateRent(0); }`。
公用事業租金會永遠是 0，屬於靜默錯誤（不會拋例外，只會算錯錢），最難查。

---

## 7. 一個容易踩的陷阱

`AbstractProperty` 在建構子裡就呼叫 `group.register(this)`
（`monopoly/property/AbstractProperty.java:36` 起），
所以 **`PropertyGroup` 的成員名單是「這塊棋盤上有哪些地產」，不是「誰擁有它們」**。
`PropertyGroup` 也**沒有** `remove` 方法。

因此：

- ✅ `PropertyGroup` 用來算「持有人持有幾筆」→ 走 `countOwnedBy(player)`，讀 `getOwner()`，永遠正確。
- ❌ 不要想把「玩家賣地」實作成「從 `PropertyGroup` 移除」——那會把地產從棋盤上弄消失。
- ✅ 「誰擁有什麼」只有兩個地方會變：`Property.setOwner(...)`，以及 `Player` 的持有清單（快取）。

---

## 8. 與主程式的關係

這次修的是 **`monopoly` 層內部的自我一致**，`oopgroup` 與 `wintervillage` **一行都不會改**：

- `oopgroup.WinterVillagePlayerHUD.Player` 沒有 `implements monopoly.Player`，不受 `removeProperty` 影響。
- HUD 有自己的 `PropertyService`，走自己的 `PropertyData`，不經過 `PropertyMarket`。

`MONOPOLY_LAYERS.md` 的 Roadmap 第 1～3 項（HUD 的 `Player` 實作 `monopoly.Player`、
`PropertyData` 實作 `monopoly.Buyable`、HUD 的 `InsufficientFundsException` 換成 `monopoly` 的）
是 6號 的整合工作，做完之後這條鏈才會真正打通。
