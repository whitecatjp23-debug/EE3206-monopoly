package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;

/** 監獄格；停留在此格代表監獄探訪，不會自動入獄。 */
public final class JailTile extends AbstractTile {
  /** 建立監獄格。 */
  public JailTile(int index, String name) {
    super(index, name);
  }

  /** 執行監獄探訪的無額外效果。 */
  @Override
  public void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException {
    // 官方規則：Jail / Just Visiting 不會把玩家送入監獄。
  }
}
