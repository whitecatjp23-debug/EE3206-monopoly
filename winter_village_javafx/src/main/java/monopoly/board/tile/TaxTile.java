package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;

/** 向玩家收取固定稅款的格子。 */
public final class TaxTile extends AbstractTile {
  private final int amount;

  /** 建立固定金額稅款格。 */
  public TaxTile(int index, String name, int amount) {
    super(index, name);
    if (amount < 0) {
      throw new IllegalArgumentException("稅款不可為負數。");
    }
    this.amount = amount;
  }

  /** 回傳稅款金額。 */
  public int getAmount() {
    return amount;
  }

  /** 扣除玩家的固定稅款。 */
  @Override
  public void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException {
    java.util.Objects.requireNonNull(player, "player");
    player.decMoney(amount);
  }
}
