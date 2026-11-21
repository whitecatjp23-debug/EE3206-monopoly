package monopoly.board.tile;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.Player;
import monopoly.board.Board;

import java.util.Objects;

/**
 * 棋盤格子的共用基底：保管索引與名稱，並在建構時檢查索引範圍。
 *
 * <p>索引與名稱在建構後不可更動，因此子類別可以安全地被多個執行緒讀取。
 */
public abstract class AbstractTile implements Tile {
  private final int index;
  private final String name;

  /**
   * 建立一格。
   *
   * @param index 棋盤索引，必須介於 0～39
   * @param name 顯示名稱，不可為 null
   * @throws IllegalArgumentException 索引超出範圍時
   */
  protected AbstractTile(int index, String name) {
    if (index < 0 || index >= Board.TILE_COUNT) {
      throw new IllegalArgumentException("格子索引必須介於 0 到 39 之間。");
    }
    this.index = index;
    this.name = Objects.requireNonNull(name, "name");
  }

  /** 回傳棋盤索引（0～39）。 */
  @Override
  public final int getIndex() {
    return index;
  }

  /** 回傳顯示名稱。 */
  @Override
  public final String getName() {
    return name;
  }

  /**
   * 執行落地效果；由具體格子定義。
   *
   * @param player 落地玩家
   * @param diceTotal 本回合骰子總點數
   * @throws InsufficientFundsException 玩家需要付款但現金不足
   * @throws BankruptcyException 玩家付款時進入破產流程
   */
  @Override
  public abstract void landOn(Player player, int diceTotal)
      throws InsufficientFundsException, BankruptcyException;

  @Override
  public String toString() {
    return index + ": " + name;
  }
}
