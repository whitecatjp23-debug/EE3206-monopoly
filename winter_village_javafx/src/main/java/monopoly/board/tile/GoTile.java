package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;

/** 起點格：玩家落地時取得 $200。 */
public final class GoTile extends AbstractTile {
  /** 建立起點格。 */
  public GoTile(int index, String name) {
    super(index, name);
  }

  /** 發放起點獎金。 */
  @Override
  public void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException {
    java.util.Objects.requireNonNull(player, "player");
    player.gainMoney(200);
  }
}
