package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;

/**
 * 棋盤上的一格。
 *
 * <p>每一格除了提供位置與顯示名稱，也負責定義玩家落地時的行為；
 * 實際效果由具體子類別實作。
 */
public interface Tile {
  /** 回傳此格在棋盤上的索引（0～39）。 */
  int getIndex();

  /** 回傳此格顯示在棋盤上的名稱。 */
  String getName();

  /**
   * 執行玩家落在此格時的規則。
   *
   * @param player 落地玩家
   * @param diceTotal 本回合骰子總點數
   * @throws InsufficientFundsException 玩家需要付款但現金不足
   * @throws BankruptcyException 玩家付款時進入破產流程
   */
  void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException;
}
