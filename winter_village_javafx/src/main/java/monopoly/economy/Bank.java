package monopoly.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 銀行：持有場上大部分紙鈔，並負責找零。
 *
 * <p>依官方規則，銀行的錢「實際上是無限的」：存貨用完時可以加印紙鈔或寫借據代替。
 * 因此本類別同時提供兩種行為：
 * <ul>
 *   <li>存貨足夠時，直接用存貨支付，場上現金總值保持守恆。</li>
 *   <li>存貨湊不出剛好金額時，加印不足的部分，並以 {@link #getPrintedNoteCount()} 記錄加印張數。</li>
 * </ul>
 *
 * <p>存貨張數由建構子傳入，本類別刻意不提供預設張數，讓開局配置（規則層）自行決定。
 */
public final class Bank {
  /** 銀行目前每種面額的存量。 */
  private final EnumMap<Denomination, Integer> inventory = new EnumMap<>(Denomination.class);
  /** 因為存貨不足而加印的鈔票張數，供 UI 提示「銀行加印」。 */
  private int printedNoteCount;
  /** 加印鈔票的總值，等於場上「多出來」的現金。 */
  private int printedValue;

  /**
   * 以指定的初始存貨建立銀行。
   *
   * @param initialInventory 每種面額的張數；未列出的面額視為 0 張
   * @throws IllegalArgumentException 張數為負數或含 null 時
   */
  public Bank(Map<Denomination, Integer> initialInventory) {
    if (initialInventory == null) {
      throw new IllegalArgumentException("初始存貨不可為 null。");
    }
    for (Map.Entry<Denomination, Integer> entry : initialInventory.entrySet()) {
      Denomination denomination = entry.getKey();
      Integer count = entry.getValue();
      if (denomination == null || count == null) {
        throw new IllegalArgumentException("初始存貨不可包含 null 面額或 null 張數。");
      }
      if (count < 0) {
        throw new IllegalArgumentException(denomination + " 的初始張數不可為負數。");
      }
      if (count > 0) {
        inventory.put(denomination, count);
      }
    }
  }

  /**
   * 以「面額, 張數」成對參數建立銀行，方便測試與小規模對局。
   *
   * <p>例如 {@code Bank.of(500, 2, 100, 10)} 表示 500 元 2 張、100 元 10 張。
   *
   * @param denominationCountPairs 面額與張數交替出現的參數列
   * @throws IllegalArgumentException 參數個數為奇數或型別不符時
   */
  public static Bank of(Object... denominationCountPairs) {
    if (denominationCountPairs == null || denominationCountPairs.length % 2 != 0) {
      throw new IllegalArgumentException("參數必須是「面額, 張數」成對出現。");
    }
    EnumMap<Denomination, Integer> map = new EnumMap<>(Denomination.class);
    for (int i = 0; i < denominationCountPairs.length; i += 2) {
      Object denominationValue = denominationCountPairs[i];
      Object count = denominationCountPairs[i + 1];
      if (!(denominationValue instanceof Integer value) || !(count instanceof Integer noteCount)) {
        throw new IllegalArgumentException("面額與張數都必須是整數。");
      }
      map.merge(Denomination.fromValue(value), noteCount, Integer::sum);
    }
    return new Bank(map);
  }

  /**
   * 從銀行支付剛好 {@code amount} 元，回傳發出的紙鈔。
   *
   * <p>先嘗試用現有存貨湊出張數最少的組合；存貨湊不出時加印不足的部分。
   * 加印的鈔票不會憑空記入存貨，只會增加 {@link #getPrintedNoteCount()}。
   *
   * @param amount 支付金額
   * @return 發出的紙鈔（由大鈔到小鈔）
   */
  public List<Banknote> withdraw(int amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("支付金額不可為負數。");
    }
    if (amount == 0) {
      return List.of();
    }

    Map<Denomination, Integer> fromInventory =
        ChangeCalculator.fewestNotes(inventory, amount).orElse(null);
    if (fromInventory != null) {
      deduct(fromInventory);
      return ChangeCalculator.toNotes(fromInventory);
    }

    // 存貨湊不出剛好金額：以可無限加印的模式計算組合，能由存貨出的先扣存貨。
    Map<Denomination, Integer> combination = ChangeCalculator.fewestNotes(amount);
    EnumMap<Denomination, Integer> fromStock = new EnumMap<>(Denomination.class);
    for (Map.Entry<Denomination, Integer> entry : combination.entrySet()) {
      int available = inventory.getOrDefault(entry.getKey(), 0);
      int takeFromStock = Math.min(available, entry.getValue());
      if (takeFromStock > 0) {
        fromStock.put(entry.getKey(), takeFromStock);
      }
      int printed = entry.getValue() - takeFromStock;
      printedNoteCount += printed;
      printedValue += printed * entry.getKey().getValue();
    }
    deduct(fromStock);
    return ChangeCalculator.toNotes(combination);
  }

  /**
   * 只使用存貨支付，存貨湊不出時拋出例外且不改動存貨。
   *
   * <p>適合需要「現金總值守恆」的嚴格對局或單元測試。
   *
   * @param amount 支付金額
   * @return 發出的紙鈔
   * @throws InsufficientCashException 存貨湊不出剛好金額
   */
  public List<Banknote> withdrawFromInventoryOnly(int amount) throws InsufficientCashException {
    if (amount < 0) {
      throw new IllegalArgumentException("支付金額不可為負數。");
    }
    Map<Denomination, Integer> combination =
        ChangeCalculator.fewestNotes(inventory, amount)
            .orElseThrow(() -> new InsufficientCashException(amount, getTotalValue()));
    deduct(combination);
    return ChangeCalculator.toNotes(combination);
  }

  /**
   * 收下玩家繳回的紙鈔（付租、繳稅、銀行找零回收）。
   *
   * @param banknotes 要存入的紙鈔
   */
  public void deposit(Collection<Banknote> banknotes) {
    if (banknotes == null || banknotes.isEmpty()) {
      return;
    }
    for (Banknote banknote : new ArrayList<>(banknotes)) {
      deposit(banknote);
    }
  }

  /**
   * 收下單張紙鈔。
   *
   * @param banknote 要存入的紙鈔
   */
  public void deposit(Banknote banknote) {
    if (banknote == null) {
      throw new IllegalArgumentException("存入的紙鈔不可為 null。");
    }
    inventory.merge(banknote.getDenomination(), 1, Integer::sum);
  }

  /**
   * 銀行目前是否能「只用存貨」湊出剛好 {@code amount} 元。
   *
   * @param amount 目標金額
   */
  public boolean canWithdrawFromInventory(int amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("金額不可為負數。");
    }
    return ChangeCalculator.fewestNotes(inventory, amount).isPresent();
  }

  /**
   * 回傳某面額目前的存貨張數。
   *
   * @param denomination 想查詢的面額
   */
  public int countOf(Denomination denomination) {
    if (denomination == null) {
      throw new IllegalArgumentException("面額不可為 null。");
    }
    return inventory.getOrDefault(denomination, 0);
  }

  /** 回傳銀行存貨總值（元），不含加印的鈔票。 */
  public int getTotalValue() {
    int total = 0;
    for (Map.Entry<Denomination, Integer> entry : inventory.entrySet()) {
      total += entry.getKey().getValue() * entry.getValue();
    }
    return total;
  }

  /** 回傳銀行存貨總張數，不含加印的鈔票。 */
  public int getTotalNoteCount() {
    int total = 0;
    for (int count : inventory.values()) {
      total += count;
    }
    return total;
  }

  /** 回傳因為存貨不足而加印的鈔票張數。 */
  public int getPrintedNoteCount() {
    return printedNoteCount;
  }

  /** 回傳因為存貨不足而加印的鈔票總值（元）。 */
  public int getPrintedValue() {
    return printedValue;
  }

  /** 回傳每種面額張數的不可變快照，供 UI 顯示銀行存貨。 */
  public Map<Denomination, Integer> getInventory() {
    return Collections.unmodifiableMap(new EnumMap<>(inventory));
  }

  /** 從存貨扣除指定的面額張數。 */
  private void deduct(Map<Denomination, Integer> notes) {
    for (Map.Entry<Denomination, Integer> entry : notes.entrySet()) {
      inventory.merge(entry.getKey(), -entry.getValue(), Integer::sum);
    }
    inventory.values().removeIf(count -> count == 0);
  }

  @Override
  public String toString() {
    return "Bank{totalValue=" + getTotalValue()
        + ", notes=" + getTotalNoteCount()
        + ", printedNotes=" + printedNoteCount
        + ", printedValue=" + printedValue + "}";
  }
}
