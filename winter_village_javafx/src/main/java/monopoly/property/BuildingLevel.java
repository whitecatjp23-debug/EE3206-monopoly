package monopoly.property;

/**
 * 街道地產的建築等級：空地、1～4 棟房屋、1 棟酒店。
 *
 * <p>等級同時是租金表的索引（0～5），也是建築規則的上限（酒店為最高級）。
 */
public enum BuildingLevel {
  /** 空地。 */
  NONE(0),
  /** 1 棟房屋。 */
  HOUSE_1(1),
  /** 2 棟房屋。 */
  HOUSE_2(2),
  /** 3 棟房屋。 */
  HOUSE_3(3),
  /** 4 棟房屋。 */
  HOUSE_4(4),
  /** 1 棟酒店（等同第 5 級）。 */
  HOTEL(5);

  private final int level;

  BuildingLevel(int level) {
    this.level = level;
  }

  /** 回傳對應租金表的數字等級（0～5）。 */
  public int getLevel() {
    return level;
  }

  /**
   * 把數字等級轉成列舉值。
   *
   * @param level 0～5 的等級
   * @throws IllegalArgumentException 超出範圍時
   */
  public static BuildingLevel fromLevel(int level) {
    for (BuildingLevel value : values()) {
      if (value.level == level) {
        return value;
      }
    }
    throw new IllegalArgumentException("建築等級必須介於 0 到 5 之間。");
  }

  /** 是否已達最高等級（酒店）。 */
  public boolean isHotel() {
    return this == HOTEL;
  }
}
