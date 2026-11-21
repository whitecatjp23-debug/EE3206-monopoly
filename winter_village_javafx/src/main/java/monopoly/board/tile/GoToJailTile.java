package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;

/** 將玩家送進監獄的格子。 */
public final class GoToJailTile extends AbstractTile {
  /** 建立進監獄格。 */
  public GoToJailTile(int index, String name) {
    super(index, name);
  }

  /** 將玩家送進監獄。 */
  @Override
  public void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException {
    java.util.Objects.requireNonNull(player, "player");
    player.sendToJail();
  }
}
