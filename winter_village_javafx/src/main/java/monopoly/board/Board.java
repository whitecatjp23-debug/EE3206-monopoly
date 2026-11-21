package monopoly.board;

import monopoly.board.tile.PropertyHolder;
import monopoly.board.tile.Tile;
import monopoly.property.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 大富翁棋盤：固定 40 格，索引 0～39，0 為 GO。
 *
 * <p>棋盤本身只負責「位置」與「格子查詢」：
 * <ul>
 *   <li>{@link #getTile(int)}：用索引取得格子；</li>
 *   <li>{@link #findTile(String)}：用名稱取得格子；</li>
 *   <li>{@link #getProperties()}：列出所有可購買地產；</li>
 *   <li>{@link #move(int, int)}：前進並處理繞過終點的回繞。</li>
 * </ul>
 *
 * <p>地產的買賣與租金計算屬於 {@code monopoly.property} 套件，
 * 事件格的效果屬於規則層，棋盤皆不介入。
 */
public final class Board {
  /** 棋盤格數，公開給格子類別驗證索引用。 */
  public static final int TILE_COUNT = 40;
  /** GO 所在的索引。 */
  public static final int GO_INDEX = 0;

  private final List<Tile> tiles;

  /**
   * 建立棋盤。
   *
   * @param tiles 40 個格子，且第 i 個格子的索引必須等於 i
   * @throws IllegalArgumentException 數量不是 40 或索引順序錯誤時
   */
  public Board(List<Tile> tiles) {
    Objects.requireNonNull(tiles, "tiles");
    if (tiles.size() != TILE_COUNT) {
      throw new IllegalArgumentException("大富翁棋盤必須剛好有 40 格。");
    }

    for (int index = 0; index < TILE_COUNT; index++) {
      Tile tile = Objects.requireNonNull(tiles.get(index), "索引 " + index + " 的格子");
      if (tile.getIndex() != index) {
        throw new IllegalArgumentException("格子索引必須由 0 依序排到 39。");
      }
    }

    this.tiles = List.copyOf(tiles);
  }

  /**
   * 取得指定索引的格子。
   *
   * @param index 0～39 的索引
   * @throws IndexOutOfBoundsException 索引超出範圍時
   */
  public Tile getTile(int index) {
    if (index < 0 || index >= TILE_COUNT) {
      throw new IndexOutOfBoundsException("格子索引必須介於 0 到 39 之間。");
    }
    return tiles.get(index);
  }

  /**
   * 依名稱尋找格子（名稱需完全相同）。
   *
   * @param name 格子名稱
   * @return 找到的格子
   * @throws IllegalArgumentException 找不到時
   */
  public Tile findTile(String name) {
    Objects.requireNonNull(name, "name");
    for (Tile tile : tiles) {
      if (tile.getName().equals(name)) {
        return tile;
      }
    }
    throw new IllegalArgumentException("棋盤上沒有名為 " + name + " 的格子。");
  }

  /**
   * 依地產尋找所在的格子。
   *
   * @param property 要查詢的地產
   * @return 該地產所在的格子索引
   * @throws IllegalArgumentException 此地產不在棋盤上時
   */
  public int indexOf(Property property) {
    Objects.requireNonNull(property, "property");
    for (Tile tile : tiles) {
      if (tile instanceof PropertyHolder holder && holder.getProperty() == property) {
        return tile.getIndex();
      }
    }
    throw new IllegalArgumentException("棋盤上沒有此地產：" + property.getName());
  }

  /** 回傳棋盤上所有可購買地產，依棋盤順序排列。 */
  public List<Property> getProperties() {
    List<Property> properties = new ArrayList<>();
    for (Tile tile : tiles) {
      if (tile instanceof PropertyHolder holder) {
        properties.add(holder.getProperty());
      }
    }
    return properties;
  }

  /**
   * 取得指定格子上的地產；事件格（GO、機會、稅…）回傳 null。
   *
   * <p>與 {@link #getProperties()} 不同的是，這裡保留「第幾格」的資訊，
   * 讓規則層在玩家停在某一格時可以直接查出該格的地產，不必自行比對索引。
   *
   * @param index 0～39 的格子索引
   * @return 該格的地產；事件格回傳 null
   * @throws IndexOutOfBoundsException 索引超出範圍時
   */
  public Property getPropertyAt(int index) {
    Tile tile = getTile(index);
    return tile instanceof PropertyHolder holder ? holder.getProperty() : null;
  }

  /**
   * 依地產名稱尋找地產（名稱需完全相同）。
   *
   * @param name 地產名稱
   * @return 找到的地產
   * @throws IllegalArgumentException 找不到時
   */
  public Property findPropertyByName(String name) {
    Objects.requireNonNull(name, "name");
    for (Property property : getProperties()) {
      if (property.getName().equals(name)) {
        return property;
      }
    }
    throw new IllegalArgumentException("棋盤上沒有名為 " + name + " 的地產。");
  }

  /** 回傳所有格子（唯讀，依索引順序）。 */
  public List<Tile> getTiles() {
    return tiles;
  }

  /** 回傳棋盤格數（固定為 40）。 */
  public int size() {
    return tiles.size();
  }

  /**
   * 計算前進後的落點，繞過第 39 格後回到 0。
   *
   * @param currentIndex 目前所在索引
   * @param steps 前進步數（不可為負數；後退請改用 {@link #moveBack(int, int)}）
   * @return 新的索引
   * @throws IllegalArgumentException 索引或步數不合法時
   */
  public int move(int currentIndex, int steps) {
    requireValidIndex(currentIndex);
    if (steps < 0) {
      throw new IllegalArgumentException("前進步數不可為負數。");
    }
    return Math.floorMod(currentIndex + steps, TILE_COUNT);
  }

  /**
   * 計算後退後的落點（例如抽到「後退 3 格」的機會卡）。
   *
   * @param currentIndex 目前所在索引
   * @param steps 後退步數（不可為負數）
   * @return 新的索引，0 之前會繞回 39
   */
  public int moveBack(int currentIndex, int steps) {
    requireValidIndex(currentIndex);
    if (steps < 0) {
      throw new IllegalArgumentException("後退步數不可為負數。");
    }
    return Math.floorMod(currentIndex - steps, TILE_COUNT);
  }

  /**
   * 判斷這次前進是否「經過或停在 GO」。
   *
   * <p>經過 GO 可領薪水，停在 GO 也算經過，因此 {@code currentIndex + steps >= 40} 即成立。
   * 步數為 0 時不算經過。
   *
   * @param currentIndex 目前所在索引
   * @param steps 前進步數
   */
  public boolean passesGo(int currentIndex, int steps) {
    requireValidIndex(currentIndex);
    if (steps < 0) {
      throw new IllegalArgumentException("前進步數不可為負數。");
    }
    return steps > 0 && currentIndex + steps >= TILE_COUNT;
  }

  /** 檢查索引是否落在 0～39。 */
  private static void requireValidIndex(int index) {
    if (index < 0 || index >= TILE_COUNT) {
      throw new IllegalArgumentException("格子索引必須介於 0 到 39 之間。");
    }
  }
}
