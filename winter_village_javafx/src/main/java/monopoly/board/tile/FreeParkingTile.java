package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;

/** 免費停車格。 */
public final class FreeParkingTile extends AbstractTile {
  /** 建立免費停車格。 */
  public FreeParkingTile(int index, String name) {
    super(index, name);
  }

  /** 執行免費停車的無額外效果。 */
  @Override
  public void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException {
    // 官方規則：免費停車不會自動派發獎金。
  }
}
