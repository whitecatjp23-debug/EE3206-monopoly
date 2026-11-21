package monopoly.property;

/**
 * 可建造房屋與酒店的地產「能力介面」。
 *
 * <p>這裡只描述<b>讀取</b>能力：目前等級、這格還能不能再建。
 * 任何呼叫端（UI 快照、抵押前的建築檢查）都能安全地問這些問題，
 * 也就不會出現「UI 自己判斷一次、規則層又判斷一次」的分歧。
 *
 * <p><b>不含 {@code build()}／{@code sellBuilding()}</b>：
 * 建築等級若能被任意呼叫端直接改動，就能繞過
 * 「集滿整組、組內無抵押、平均建造、付款」這些規則。
 * 因此增建與拆除只留在 {@link AbstractDevelopableProperty} 的套件可見方法，
 * 由 {@link PropertyDevelopment} 在檢查與扣款完成後呼叫。
 */
public interface Developable {
  /** 回傳目前的建築等級。 */
  BuildingLevel getBuildingLevel();

  /** 回傳這一格是否還能再加一棟建築（未抵押且未達酒店）。 */
  boolean canBuild();
}
