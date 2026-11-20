package monopoly.economy;

import java.util.List;

/**
 * 大富翁紙鈔的面額。
 *
 * <p>面額由小到大排列，{@link #getValue()} 回傳該面額代表的金額（以元為單位）。
 * 銀行、錢包與 UI 都以此列舉作為唯一的金額來源，避免各處硬寫數字造成不一致。
 */
public enum Denomination {
  ONE(1),
  FIVE(5),
  TEN(10),
  TWENTY(20),
  FIFTY(50),
  ONE_HUNDRED(100),
  FIVE_HUNDRED(500);

  /** 由大到小的面額，發鈔與找零時優先使用大鈔以減少張數。 */
  private static final List<Denomination> LARGEST_FIRST =
      List.of(FIVE_HUNDRED, ONE_HUNDRED, FIFTY, TWENTY, TEN, FIVE, ONE);

  private final int value;

  Denomination(int value) {
    this.value = value;
  }

  /** 回傳這張鈔票的幣值（元）。 */
  public int getValue() {
    return value;
  }

  /**
   * 依幣值尋找對應面額。
   *
   * @param value 幣值，例如 20
   * @return 對應的面額
   * @throws IllegalArgumentException 當 {@code value} 不是任何一種面額時
   */
  public static Denomination fromValue(int value) {
    for (Denomination denomination : values()) {
      if (denomination.value == value) {
        return denomination;
      }
    }
    throw new IllegalArgumentException("沒有面額為 " + value + " 的鈔票。");
  }

  /** 回傳由大到小排序的面額清單；回傳值不可變，呼叫端不需自行排序。 */
  public static List<Denomination> largestFirst() {
    return LARGEST_FIRST;
  }
}
