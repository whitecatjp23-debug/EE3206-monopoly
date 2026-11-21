package monopoly.property;

/**
 * 可建造地產的共用基底：保管建築等級，並提供增建／拆除的內部操作。
 *
 * <p>對外只公開「讀取」能力（{@link #getBuildingLevel()}、{@link #canBuild()}）；
 * {@link #build()} 與 {@link #sellBuilding()} 刻意維持套件可見，
 * 只有同套件的 {@link PropertyDevelopment} 能呼叫。
 *
 * <p>理由和 {@link PropertyState} 相同：建築等級不能讓任意呼叫端直接改，
 * 否則就能繞過「集滿整組、組內無抵押、平均建造、付款」這些規則。
 * 正常流程一律走 {@link PropertyDevelopment}。
 *
 * <p>這個類別放在 {@code monopoly.property} 而不是 {@code .type}，
 * 是為了讓套件可見的增建方法真的只對規則服務開放。
 */
public abstract class AbstractDevelopableProperty extends AbstractProperty implements Developable {
  private int buildingLevel;

  /**
   * 建立可建造地產。
   *
   * @param name 地產名稱
   * @param purchasePrice 購買價格
   * @param group 所屬組別
   */
  protected AbstractDevelopableProperty(String name, int purchasePrice, PropertyGroup group) {
    super(name, purchasePrice, group);
  }

  /** 回傳目前的建築等級。 */
  @Override
  public final BuildingLevel getBuildingLevel() {
    return BuildingLevel.fromLevel(buildingLevel);
  }

  /** 回傳這一格是否還能再加一棟建築（未抵押且未達酒店）。 */
  @Override
  public final boolean canBuild() {
    return !isMortgaged() && buildingLevel < BuildingLevel.HOTEL.getLevel();
  }

  /**
   * 增加一級建築（第 4 棟房屋之後即為酒店）。
   *
   * <p>只供 {@link PropertyDevelopment} 在規則檢查與扣款完成後呼叫。
   *
   * @throws IllegalStateException 這一格已達上限或抵押中
   */
  final void build() {
    if (!canBuild()) {
      throw new IllegalStateException("此地產無法再增建（已達酒店或抵押中）。");
    }
    buildingLevel++;
  }

  /**
   * 減少一級建築（酒店退回 4 棟房屋）。
   *
   * <p>只供 {@link PropertyDevelopment} 在規則檢查後呼叫。
   *
   * @throws IllegalStateException 這一格沒有任何建築
   */
  final void sellBuilding() {
    if (buildingLevel == 0) {
      throw new IllegalStateException("此地產沒有建築可拆除。");
    }
    buildingLevel--;
  }
}
