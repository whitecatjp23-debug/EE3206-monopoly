package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;
import monopoly.property.Property;
import monopoly.property.PropertyMarket;

import java.util.Objects;

/**
 * 可購買地產所在的格子（街道、鐵路、公用事業）。
 *
 * <p>格子只負責「把位置對應到地產」與「踩到時轉呼叫規則」：
 * 買賣、抵押、收租等規則全部在 {@code monopoly.property} 的服務裡。
 *
 * <p>交易服務由外部注入，而不是在這裡 {@code new} 一個。
 * 這樣棋盤上的每一格都共用同一份規則實作，測試也能換上自己的實作；
 * 若在格子內部自行建立，規則實作就會被鎖死在建構子裡，無法替換也難以觀察。
 */
public final class PropertyTile extends AbstractTile implements PropertyHolder {
  private final Property property;
  private final PropertyMarket market;

  /**
   * 建立地產格，格子的名稱直接沿用該地產的名稱。
   *
   * @param index 棋盤索引（0～39）
   * @param property 此格代表的地產
   * @param market 收租等交易規則所用的服務，通常整副棋盤共用一個
   */
  public PropertyTile(int index, Property property, PropertyMarket market) {
    super(index, Objects.requireNonNull(property, "property").getName());
    this.property = property;
    this.market = Objects.requireNonNull(market, "market");
  }

  /**
   * 執行地產格落地規則。
   *
   * <p>無主地產只回報落地，不自動購買；購買決定由遊戲或 UI 處理。
   * 若由其他玩家持有，則依骰子總點數收租。
   */
  @Override
  public void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException {
    Objects.requireNonNull(player, "player");
    Player owner = property.getOwner();
    if (owner == null || owner.equals(player)) {
      return;
    }
    market.payRent(player, property, diceTotal);
  }

  /** 回傳此格代表的地產。 */
  @Override
  public Property getProperty() {
    return property;
  }
}
