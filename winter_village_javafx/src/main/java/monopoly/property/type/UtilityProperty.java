package monopoly.property.type;

import monopoly.property.AbstractProperty;
import monopoly.property.PropertyGroup;

/**
 * 公用事業：租金為骰子點數的倍數，不能蓋房屋。
 *
 * <p>官方規則：持有人只有 1 項時為骰數 ×4，擁有 2 項時為骰數 ×10。
 */
public final class UtilityProperty extends AbstractProperty {
  /** 只擁有 1 項公用事業時的倍數。 */
  private static final int SINGLE_MULTIPLIER = 4;
  /** 擁有 2 項公用事業時的倍數。 */
  private static final int DOUBLE_MULTIPLIER = 10;

  /**
   * 建立公用事業地產。
   *
   * @param name 名稱（例如 Electric Company）
   * @param purchasePrice 購買價格（經典版為 $150）
   * @param group 公用事業組別（用來判斷持有人擁有幾項）
   */
  public UtilityProperty(String name, int purchasePrice, PropertyGroup group) {
    super(name, purchasePrice, group);
  }

  /**
   * 回傳租金倍率表副本：索引 0 為持有 1 項時的 ×4，索引 1 為持有 2 項時的 ×10。
   *
   * <p>供地契顯示用；實際租金仍需 {@link #calculateRent(int)} 乘上骰數。
   */
  @Override
  public int[] getRentTiers() {
    return new int[] {SINGLE_MULTIPLIER, DOUBLE_MULTIPLIER};
  }

  /**
   * 計算租金：骰子點數 ×4（擁有 1 項）或 ×10（擁有 2 項）；抵押中或無人持有時為 0。
   *
   * @param diceTotal 兩顆骰子的點數和
   */
  @Override
  public int calculateRent(int diceTotal) {
    if (diceTotal < 0) {
      throw new IllegalArgumentException("骰子點數不可為負數。");
    }
    if (isMortgaged() || !isOwned()) {
      return 0;
    }
    int owned = getGroup().countOwnedBy(getOwner());
    return diceTotal * (owned >= 2 ? DOUBLE_MULTIPLIER : SINGLE_MULTIPLIER);
  }
}
