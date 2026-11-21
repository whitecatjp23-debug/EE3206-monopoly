package monopoly.property.type;

import monopoly.property.AbstractProperty;
import monopoly.property.PropertyGroup;

/**
 * 鐵路地產：租金依「持有人擁有的鐵路數」決定，不能蓋房屋。
 *
 * <p>官方租金表：1 條 $25、2 條 $50、3 條 $100、4 條 $200。
 */
public final class RailroadProperty extends AbstractProperty {
  /** 依持有數量（索引）查詢的租金表。 */
  private static final int[] RENT_BY_COUNT = {0, 25, 50, 100, 200};

  /**
   * 建立鐵路地產。
   *
   * @param name 鐵路名稱
   * @param purchasePrice 購買價格（經典版為 $200）
   * @param group 鐵路組別（用來統計持有人擁有幾條鐵路）
   */
  public RailroadProperty(String name, int purchasePrice, PropertyGroup group) {
    super(name, purchasePrice, group);
  }

  /**
   * 回傳租金表副本：索引 0～3 依序為持有 1～4 條鐵路時的租金。
   *
   * <p>供地契顯示用；實際租金仍需 {@link #calculateRent(int)} 依持有數查表。
   */
  @Override
  public int[] getRentTiers() {
    // 索引 0 是「持有 0 條」的佔位值，不對外顯示。
    return new int[] {
        RENT_BY_COUNT[1], RENT_BY_COUNT[2], RENT_BY_COUNT[3], RENT_BY_COUNT[4],
    };
  }

  /**
   * 計算租金：依持有人擁有的鐵路數量查表；抵押中或無人持有時為 0。
   *
   * @param diceTotal 骰子點數（鐵路不使用，保留給統一介面）
   */
  @Override
  public int calculateRent(int diceTotal) {
    if (isMortgaged() || !isOwned()) {
      return 0;
    }
    int count = getGroup().countOwnedBy(getOwner());
    int index = Math.max(0, Math.min(count, RENT_BY_COUNT.length - 1));
    return RENT_BY_COUNT[index];
  }
}
