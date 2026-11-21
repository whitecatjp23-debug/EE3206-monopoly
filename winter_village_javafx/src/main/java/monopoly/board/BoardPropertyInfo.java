package monopoly.board;

import monopoly.property.Developable;
import monopoly.property.Property;

/**
 * 棋盤上單一地產的可讀快照，供 UI 建立自己的顯示模型。
 *
 * <p>UI 層（例如 {@code oopgroup}）有自己綁 JavaFX 屬性的地產類別，
 * 不能直接持有 {@link Property}；這個 record 只提供「建立顯示資料所需的一切」，
 * 讓 UI 不必再自己抄一份名稱、價格與租金表，也就不會出現兩份資料不同步的問題。
 *
 * @param tileIndex 格子索引（0～39）
 * @param name 地產名稱
 * @param groupName 組別名稱（{@code Brown}、{@code Light Blue}、{@code Railroad} …），
 *     UI 可據此對應自己的顏色組別列舉
 * @param purchasePrice 購買價格
 * @param rentTiers 租金表副本；街道為 6 級、鐵路為持有 1～4 條、公用事業為 ×4／×10
 * @param buildable 是否可蓋房屋與酒店
 * @param buildingCost 每棟房屋／酒店的建造費用；不可建造時為 0
 */
public record BoardPropertyInfo(
    int tileIndex,
    String name,
    String groupName,
    int purchasePrice,
    int[] rentTiers,
    boolean buildable,
    int buildingCost) {

  /**
   * 建立快照。
   *
   * <p>租金表會被複製一份，避免呼叫端改動到棋盤上的地產。
   *
   * @param tileIndex 格子索引
   * @param property 要快照的地產
   */
  public BoardPropertyInfo(int tileIndex, Property property) {
    this(
        tileIndex,
        property.getName(),
        property.getGroup().getName(),
        property.getPurchasePrice(),
        property.getRentTiers(),
        isBuildable(property),
        getBuildingCost(property));
  }

  /** 回傳租金表副本（record 的存取子會直接回傳陣列，這裡改成副本以維持不可變）。 */
  @Override
  public int[] rentTiers() {
    return rentTiers.clone();
  }

  /** 只有實作 {@link Developable} 的地產能蓋房屋與酒店。 */
  private static boolean isBuildable(Property property) {
    return property instanceof Developable;
  }

  /** 回傳可建造地產所屬組別的建造費用。 */
  private static int getBuildingCost(Property property) {
    return property instanceof Developable ? property.getGroup().getBuildingCost() : 0;
  }

  /**
   * 回傳組別的鍵值，可直接對應 UI 的顏色組別列舉常數。
   *
   * <p>例：{@code Brown} → {@code BROWN}、{@code Light Blue} → {@code LIGHT_BLUE}。
   *
   * @return 大寫、空白換成底線的組別名稱
   */
  public String groupKey() {
    return groupName.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
  }

  /** 回傳所屬組別的可讀描述，供除錯輸出用。 */
  @Override
  public String toString() {
    return name + " (#" + tileIndex + ", " + groupName + ", $" + purchasePrice + ")";
  }
}
