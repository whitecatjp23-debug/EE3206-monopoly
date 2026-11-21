package monopoly.property.type;

import monopoly.property.AbstractDevelopableProperty;
import monopoly.property.BuildingLevel;
import monopoly.property.PropertyGroup;

/**
 * 街道地產：可蓋房屋與酒店，租金依建築等級與是否集滿整組計算。
 *
 * <p>租金表固定六個等級（空地、1～4 棟房屋、酒店），
 * 集滿整組且尚未蓋房屋時，空地租金加倍（官方規則）。
 *
 * <p>建築等級由 {@link AbstractDevelopableProperty} 保管，
 * 本類別只留下「租金怎麼算」與「租金表長什麼樣」這兩件事。
 */
public final class StreetProperty extends AbstractDevelopableProperty {
  /** 租金表的等級數量：空地 + 4 棟房屋 + 酒店。 */
  public static final int RENT_LEVELS = 6;

  private final int[] rentTable;

  /**
   * 建立街道地產。
   *
   * @param name 地產名稱
   * @param purchasePrice 購買價格
   * @param group 所屬顏色組別
   * @param rentTable 六個等級的租金，索引 0 為空地、索引 5 為酒店
   */
  public StreetProperty(String name, int purchasePrice, PropertyGroup group, int[] rentTable) {
    super(name, purchasePrice, group);
    if (rentTable == null || rentTable.length != RENT_LEVELS) {
      throw new IllegalArgumentException("街道租金表必須包含 6 個等級。");
    }
    for (int rent : rentTable) {
      if (rent < 0) {
        throw new IllegalArgumentException("租金不可為負數。");
      }
    }
    this.rentTable = rentTable.clone();
  }

  /**
   * 回傳租金表的防禦性副本（索引 0～5）。
   *
   * @deprecated 請改用 {@link #getRentTiers()}，與其他地產共用同一個介面方法。
   */
  @Deprecated
  public int[] getRentTable() {
    return getRentTiers();
  }

  /** 回傳租金表副本（{@link #getRentTiers()} 的實作）。 */
  @Override
  public int[] getRentTiers() {
    return rentTable.clone();
  }

  /**
   * 回傳指定等級的租金（不含整組加倍）。
   *
   * @param level 0～5 的建築等級
   */
  public int getRentAt(int level) {
    return rentTable[BuildingLevel.fromLevel(level).getLevel()];
  }

  /**
   * 計算租金：依建築等級查表；空地且持有人集滿整組時加倍；抵押中不收租。
   *
   * @param diceTotal 骰子點數（街道不使用，保留給統一介面）
   */
  @Override
  public int calculateRent(int diceTotal) {
    if (isMortgaged() || !isOwned()) {
      return 0;
    }
    int level = getBuildingLevel().getLevel();
    int rent = rentTable[level];
    if (level == 0 && getGroup().isCompleteFor(getOwner())) {
      return rent * 2;
    }
    return rent;
  }

  @Override
  public String toString() {
    return getName() + " (" + getGroup().getName() + ", 等級 "
        + getBuildingLevel().getLevel() + ")";
  }
}
