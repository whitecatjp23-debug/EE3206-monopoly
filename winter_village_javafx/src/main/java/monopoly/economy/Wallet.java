package monopoly.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 玩家的實體錢包：實際持有的一疊紙鈔。
 *
 * <p>錢包只負責保管鈔票與計算「付得出多少」，不處理交易規則。
 * 大富翁的正式規則是「只要有足夠現金就付得出來，銀行負責找零」，
 * 因此付款時只要拿出總值不小於金額的鈔票即可，差額由 {@link CashTransaction} 讓銀行找零。
 */
public final class Wallet {
  /** 每種面額目前持有的張數。 */
  private final EnumMap<Denomination, Integer> notes = new EnumMap<>(Denomination.class);

  /** 建立空錢包。 */
  public Wallet() {
  }

  /**
   * 以既有鈔票建立錢包（常用於開局派發或讀檔）。
   *
   * @param banknotes 初始持有的紙鈔
   */
  public Wallet(Collection<Banknote> banknotes) {
    addAll(banknotes);
  }

  /**
   * 放入一張鈔票。
   *
   * @param banknote 要放入的紙鈔
   */
  public void add(Banknote banknote) {
    Objects.requireNonNull(banknote, "banknote");
    notes.merge(banknote.getDenomination(), 1, Integer::sum);
  }

  /**
   * 一次放入多張鈔票。
   *
   * @param banknotes 要放入的紙鈔
   */
  public void addAll(Collection<Banknote> banknotes) {
    Objects.requireNonNull(banknotes, "banknotes");
    for (Banknote banknote : new ArrayList<>(banknotes)) {
      add(banknote);
    }
  }

  /** 回傳錢包內鈔票的總值（元）。 */
  public int getBalance() {
    int total = 0;
    for (Map.Entry<Denomination, Integer> entry : notes.entrySet()) {
      total += entry.getKey().getValue() * entry.getValue();
    }
    return total;
  }

  /** 回傳錢包內是否完全沒有鈔票。 */
  public boolean isEmpty() {
    return notes.isEmpty();
  }

  /**
   * 回傳目前持有某面額的張數。
   *
   * @param denomination 想查詢的面額
   */
  public int countOf(Denomination denomination) {
    return notes.getOrDefault(Objects.requireNonNull(denomination, "denomination"), 0);
  }

  /** 回傳目前持有鈔票的張數總和。 */
  public int getNoteCount() {
    int total = 0;
    for (int count : notes.values()) {
      total += count;
    }
    return total;
  }

  /**
   * 拿出總值「不小於」{@code amount} 的一組鈔票，差額由銀行找零。
   *
   * <p>由大鈔開始累加，直到總值足夠為止；因為現金不足以支付時直接失敗，
   * 所以本方法保證只要餘額足夠就一定能拿出鈔票。
   *
   * @param amount 要支付的金額
   * @return 交出的紙鈔（總值可能大於 {@code amount}）
   * @throws InsufficientCashException 錢包總值不足
   */
  public List<Banknote> takeCovering(int amount) throws InsufficientCashException {
    if (amount < 0) {
      throw new IllegalArgumentException("付款金額不可為負數。");
    }
    if (amount == 0) {
      return List.of();
    }
    int balance = getBalance();
    if (balance < amount) {
      throw new InsufficientCashException(amount, balance);
    }

    List<Banknote> hand = new ArrayList<>();
    int covered = 0;
    for (Banknote banknote : getNotes()) {
      if (covered >= amount) {
        break;
      }
      hand.add(banknote);
      covered += banknote.getValue();
    }

    for (Banknote banknote : hand) {
      notes.merge(banknote.getDenomination(), -1, Integer::sum);
    }
    notes.values().removeIf(count -> count == 0);
    return List.copyOf(hand);
  }

  /**
   * 拿出錢包內所有鈔票並清空錢包。
   *
   * @return 全部紙鈔，順序由大鈔到小鈔
   */
  public List<Banknote> takeAll() {
    List<Banknote> all = getNotes();
    notes.clear();
    return all;
  }

  /** 回傳目前持有鈔票的快照，由大鈔排到小鈔。 */
  public List<Banknote> getNotes() {
    return List.copyOf(ChangeCalculator.toNotes(notes));
  }

  /** 回傳每種面額張數的不可變快照，供 UI 顯示。 */
  public Map<Denomination, Integer> getNoteCounts() {
    return Collections.unmodifiableMap(new EnumMap<>(notes));
  }

  @Override
  public String toString() {
    return "Wallet{balance=" + getBalance() + ", notes=" + getNoteCount() + "}";
  }
}
