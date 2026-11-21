package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;

/** 社區福利格；抽卡效果由事件系統在此格落地後處理。 */
public final class CommunityChestTile extends AbstractTile {
  /** 建立社區福利格。 */
  public CommunityChestTile(int index, String name) {
    super(index, name);
  }

  /** 保留給事件系統接手社區福利卡效果。 */
  @Override
  public void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException {
    // 事件系統尚未接入；此格本身不直接改變玩家狀態。
  }
}
