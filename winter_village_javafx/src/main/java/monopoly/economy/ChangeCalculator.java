package monopoly.economy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 鈔票組合計算工具：在「有限的存貨」或「不限張數」兩種情境下找出張數最少的組合。
 *
 * <p>不能使用「由大到小貪心」：面額 20 與 50 之間沒有整除關係，例如要湊 60 元、
 * 手上是 50×1 + 20×3 時，貪心會先拿 50 而失敗，但 20×3 是可行解。
 * 因此這裡用帶記憶化的回溯搜尋，保證在張數最少的解中挑一個。
 */
final class ChangeCalculator {
  private ChangeCalculator() {
  }

  /**
   * 從 {@code available} 有限的張數中找出總額等於 {@code amount} 的組合。
   *
   * @param available 每種面額可用的張數
   * @param amount 目標金額，必須大於等於 0
   * @return 張數最少的組合；湊不出來時回傳 {@link Optional#empty()}
   */
  static Optional<EnumMap<Denomination, Integer>> fewestNotes(
      Map<Denomination, Integer> available, int amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("目標金額不可為負數。");
    }
    if (amount == 0) {
      return Optional.of(new EnumMap<>(Denomination.class));
    }

    List<Denomination> order = Denomination.largestFirst();
    int[] limits = new int[order.size()];
    long totalValue = 0;
    for (int i = 0; i < order.size(); i++) {
      limits[i] = Math.max(0, available.getOrDefault(order.get(i), 0));
      totalValue += (long) limits[i] * order.get(i).getValue();
    }
    if (totalValue < amount) {
      return Optional.empty();
    }
    return solve(order, limits, amount);
  }

  /**
   * 不限張數時，找出總額等於 {@code amount} 且張數最少的組合（銀行加印紙鈔時使用）。
   *
   * @param amount 目標金額，必須大於等於 0
   * @return 張數最少的組合
   */
  static EnumMap<Denomination, Integer> fewestNotes(int amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("目標金額不可為負數。");
    }
    List<Denomination> order = Denomination.largestFirst();
    int[] limits = new int[order.size()];
    for (int i = 0; i < order.size(); i++) {
      limits[i] = amount / order.get(i).getValue();
    }
    // 面額含 1 元，因此一定有解。
    return solve(order, limits, amount).orElseThrow();
  }

  /** 共用的回溯搜尋，回傳張數最少的組合。 */
  private static Optional<EnumMap<Denomination, Integer>> solve(
      List<Denomination> order, int[] limits, int amount) {
    Solver solver = new Solver(order, limits, amount);
    int[] solution = solver.solve(0, amount);
    if (solution == null) {
      return Optional.empty();
    }

    EnumMap<Denomination, Integer> result = new EnumMap<>(Denomination.class);
    for (int i = 0; i < order.size(); i++) {
      if (solution[i] > 0) {
        result.put(order.get(i), solution[i]);
      }
    }
    return Optional.of(result);
  }

  /**
   * 把「面額 → 張數」轉換成實體鈔票清單，順序由大鈔到小鈔。
   *
   * @param notes 每種面額的張數
   * @return 對應的紙鈔清單
   */
  static List<Banknote> toNotes(Map<Denomination, Integer> notes) {
    List<Banknote> result = new ArrayList<>();
    for (Denomination denomination : Denomination.largestFirst()) {
      int count = notes.getOrDefault(denomination, 0);
      for (int i = 0; i < count; i++) {
        result.add(new Banknote(denomination));
      }
    }
    return result;
  }

  /**
   * 把鈔票清單統計成「面額 → 張數」。
   *
   * @param banknotes 紙鈔清單
   * @return 每種面額的張數
   */
  static EnumMap<Denomination, Integer> count(List<Banknote> banknotes) {
    EnumMap<Denomination, Integer> counts = new EnumMap<>(Denomination.class);
    for (Banknote banknote : banknotes) {
      counts.merge(banknote.getDenomination(), 1, Integer::sum);
    }
    return counts;
  }

  /**
   * 合併兩份「面額 → 張數」表格，回傳新的 map。
   *
   * @param first 第一份表格
   * @param second 第二份表格
   * @return 合併結果
   */
  static EnumMap<Denomination, Integer> merge(
      Map<Denomination, Integer> first, Map<Denomination, Integer> second) {
    EnumMap<Denomination, Integer> merged = new EnumMap<>(Denomination.class);
    merged.putAll(first);
    for (Map.Entry<Denomination, Integer> entry : second.entrySet()) {
      merged.merge(entry.getKey(), entry.getValue(), Integer::sum);
    }
    return merged;
  }

  /** 記憶化回溯：{@code solve(index, remaining)} 回傳面額 index 以後的最小張數解。 */
  private static final class Solver {
    private final List<Denomination> order;
    private final int[] limits;
    private final int amount;
    /** key = index * (amount + 1) + remaining，value = 該狀態的最佳解（長度為面額數）。 */
    private final Map<Long, int[]> memo = new HashMap<>();
    /** 已確認無解的狀態，避免重複展開。 */
    private final Map<Long, Boolean> failed = new HashMap<>();

    private Solver(List<Denomination> order, int[] limits, int amount) {
      this.order = order;
      this.limits = limits;
      this.amount = amount;
    }

    /**
     * 回傳湊出 {@code remaining} 的最佳張數組合，湊不出來時回傳 null。
     *
     * @param index 目前考慮的面額索引（由大排到小）
     * @param remaining 尚未湊到的金額
     */
    private int[] solve(int index, int remaining) {
      if (remaining == 0) {
        return new int[order.size()];
      }
      if (index == order.size()) {
        return null;
      }

      long key = (long) index * (amount + 1L) + remaining;
      int[] cached = memo.get(key);
      if (cached != null) {
        return cached;
      }
      if (failed.containsKey(key)) {
        return null;
      }

      int value = order.get(index).getValue();
      int maxCount = Math.min(limits[index], remaining / value);
      // 張數的理論下限：每張最多只值最大面額，已達下限就不可能更少。
      int maxDenomination = order.get(0).getValue();
      int lowerBound = (remaining + maxDenomination - 1) / maxDenomination;

      int[] best = null;
      int bestTotal = Integer.MAX_VALUE;
      for (int count = maxCount; count >= 0; count--) {
        int[] rest = solve(index + 1, remaining - count * value);
        if (rest == null) {
          continue;
        }
        int total = count;
        for (int restCount : rest) {
          total += restCount;
        }
        if (total < bestTotal) {
          bestTotal = total;
          best = rest.clone();
          best[index] = count;
        }
        if (bestTotal == lowerBound) {
          break;
        }
      }

      if (best == null) {
        failed.put(key, Boolean.TRUE);
      } else {
        memo.put(key, best);
      }
      return best;
    }
  }
}
