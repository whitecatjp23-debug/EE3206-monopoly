package monopoly.property;

import monopoly.Player;
import monopoly.Buyable;

import java.util.Objects;

/**
 * 所有可購買地產（街道、鐵路、公用事業）共用的介面。
 *
 * <p>此介面只描述「讀取狀態」與「依規則算租金」，不負責轉帳；
 * 購買、抵押、收租等流程由 {@link PropertyMarket} 統一處理，
 * 建造房屋與酒店由 {@link PropertyDevelopment} 處理。
 */
public interface Property extends Buyable {
  /** 回傳地產名稱（例如 Mediterranean Avenue）。 */
  String getName();

  /** 回傳購買價格。 */
  int getPurchasePrice();

  /** 對 Buyable 介面提供統一的價格名稱。 */
  @Override
  default int getPrice() {
    return getPurchasePrice();
  }

  /** 回傳此地產所屬的組別；鐵路與公用事業各有獨立組別。 */
  PropertyGroup getGroup();

  /** 回傳目前持有人；無人持有時回傳 null。 */
  Player getOwner();

  /** 是否已被某位玩家持有。 */
  default boolean isOwned() {
    return getOwner() != null;
  }

  /**
   * 是否由指定玩家持有。
   *
   * @param player 要查詢的玩家，可為 null（此時一律回傳 false）
   */
  default boolean isOwnedBy(Player player) {
    return player != null && player.equals(getOwner());
  }

  /** 是否處於抵押狀態；抵押中的地產不收租。 */
  boolean isMortgaged();

  /** 回傳抵押可向銀行取得的金額（購買價的一半）。 */
  int getMortgageValue();

  /** 回傳贖回抵押所需的金額（抵押金加上一成利息）。 */
  int getUnmortgageCost();

  /**
   * 回傳抵押期間的利息（官方規則：抵押地產轉手時，新持有人須先付這筆利息）。
   *
   * @return 利息金額，等於 {@link #getUnmortgageCost()} 減去 {@link #getMortgageValue()}
   */
  default int getMortgageInterest() {
    return getUnmortgageCost() - getMortgageValue();
  }

  /**
   * 依骰子點數計算應付租金。
   *
   * <p>無人持有或已抵押時回傳 0；鐵路與公用事業的租金會依持有人擁有的數量變動，
   * 街道的租金則依房屋／酒店等級與是否集滿整組計算。
   *
   * @param diceTotal 兩顆骰子的點數和
   * @return 應付租金
   */
  int calculateRent(int diceTotal);

  /**
   * 回傳租金表，供地契（title deed）等 UI 直接列出各級租金。
   *
   * <p>三種地產的「級數」意義不同：
   * <ul>
   *   <li>街道：索引 0～5 依序為空地、1～4 棟房屋、酒店；</li>
   *   <li>鐵路：索引 0～3 依序為持有 1～4 條鐵路時的租金；</li>
   *   <li>公用事業：索引 0～1 依序為持有 1 項、2 項時的骰數倍率。</li>
   * </ul>
   * 回傳的是副本，呼叫端修改不影響地產本身。
   *
   * @return 租金表副本
   */
  int[] getRentTiers();

  /**
   * 依名稱與價格建立基本的地產描述，供除錯與測試輸出使用。
   *
   * @param property 要描述的地產
   * @return 例：{@code Mediterranean Avenue ($60, Brown)}
   */
  static String describe(Property property) {
    Objects.requireNonNull(property, "property");
    return String.format("%s ($%d, %s)",
        property.getName(), property.getPurchasePrice(), property.getGroup().getName());
  }
}
