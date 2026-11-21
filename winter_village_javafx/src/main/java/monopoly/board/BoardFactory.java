package monopoly.board;

import monopoly.board.tile.ChanceTile;
import monopoly.board.tile.CommunityChestTile;
import monopoly.board.tile.FreeParkingTile;
import monopoly.board.tile.GoTile;
import monopoly.board.tile.GoToJailTile;
import monopoly.board.tile.JailTile;
import monopoly.board.tile.PropertyTile;
import monopoly.board.tile.TaxTile;
import monopoly.board.tile.Tile;
import monopoly.property.PropertyGroup;
import monopoly.property.PropertyMarket;
import monopoly.property.type.RailroadProperty;
import monopoly.property.type.StreetProperty;
import monopoly.property.type.UtilityProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 建立大富翁棋盤（40 格、8 組街道、4 條鐵路、2 項公用事業）。
 *
 * <p>棋盤順序與正式棋盤一致：0 為 GO，逆時針依序 1～39。
 * 街道的六級租金表、鐵路的 25／50／100／200、公用事業的 4 倍與 10 倍骰數，
 * 都使用官方數值；建造費用依組別為 50／50／100／100／150／150／200／200。
 *
 * <p>價格、租金表與組別是共用的，只有「顯示名稱」不同，因此提供兩份名稱表：
 * <ul>
 *   <li>{@link #createClassicBoard()}：經典英文名稱（Mediterranean Avenue、Boardwalk）；</li>
 *   <li>{@link #createWinterVillageBoard()}：冬季村莊主題名稱（Frozen Brook Trail、Winter Summit Citadel）。</li>
 * </ul>
 * 兩者由同一份資料表產生，所以格數、索引、價格、租金一定不會分歧。
 */
public final class BoardFactory {
  /** 經典版街道名稱，按照棋盤順序排列於第 1、3、6、8 至 39 格。 */
  private static final String[] CLASSIC_STREETS = {
      "Mediterranean Avenue", "Baltic Avenue",
      "Oriental Avenue", "Vermont Avenue", "Connecticut Avenue",
      "St. Charles Place", "States Avenue", "Virginia Avenue",
      "St. James Place", "Tennessee Avenue", "New York Avenue",
      "Kentucky Avenue", "Indiana Avenue", "Illinois Avenue",
      "Atlantic Avenue", "Ventnor Avenue", "Marvin Gardens",
      "Pacific Avenue", "North Carolina Avenue", "Pennsylvania Avenue",
      "Park Place", "Boardwalk",
  };

  /** 冬季村莊版街道名稱，對應經典版街道的相同棋盤位置。 */
  private static final String[] WINTER_STREETS = {
      "Frozen Brook Trail", "Old Mill Cabin",
      "Icicle Hollow", "Snowdrift Lane", "Crystal Cavern",
      "Aurora Trail", "Frosty Pines", "Winterberry Way",
      "Fireside Lodge", "Cozy Cabin", "Hearthstone Inn",
      "Cardinal Peak", "Redwood Retreat", "Ruby Ridge",
      "Sunlit Ridge", "Golden Glade", "Sunshine Summit",
      "Evergreen Pine", "Whispering Woods", "Forest Haven",
      "Glacier Palace", "Winter Summit Citadel",
  };

  /** 經典鐵路名稱，依棋盤順序排列（5、15、25、35）。 */
  private static final String[] CLASSIC_RAILROADS = {
      "Reading Railroad", "Pennsylvania Railroad", "B. & O. Railroad", "Short Line",
  };

  /** 冬季村莊鐵路名稱，對應 {@link #CLASSIC_RAILROADS} 的同一個位置。 */
  private static final String[] WINTER_RAILROADS = {
      "North Star Station", "Snowy Express", "Polar Express", "Glacier Express",
  };

  /** 經典公用事業名稱，依棋盤順序排列（12、28）。 */
  private static final String[] CLASSIC_UTILITIES = {"Electric Company", "Water Works"};

  /** 冬季村莊公用事業名稱，對應 {@link #CLASSIC_UTILITIES} 的同一個位置。 */
  private static final String[] WINTER_UTILITIES = {"Alpine Power", "Meltwater Works"};

  private BoardFactory() {
  }

  /** 建立經典 40 格棋盤（使用經典英文地產名稱）。 */
  public static Board createClassicBoard() {
    return create(CLASSIC_STREETS, CLASSIC_RAILROADS, CLASSIC_UTILITIES);
  }

  /**
   * 建立冬季村莊主題的 40 格棋盤。
   *
   * <p>格數、索引、價格、租金表與組別都和 {@link #createClassicBoard()} 完全相同，
   * 只有顯示名稱換成遊戲中使用的主題名稱，因此主程式可以直接改用這份棋盤資料。
   */
  public static Board createWinterVillageBoard() {
    return create(WINTER_STREETS, WINTER_RAILROADS, WINTER_UTILITIES);
  }

  /**
   * 依名稱表建立棋盤。
   *
   * @param streets 22 條街道的名稱，依棋盤順序排列
   * @param railroads 4 條鐵路的名稱，依棋盤順序排列
   * @param utilities 2 項公用事業的名稱，依棋盤順序排列
   * @throws IllegalArgumentException 名稱數量不符時
   */
  private static Board create(String[] streets, String[] railroads, String[] utilities) {
    requireCount(streets, 22, "街道");
    requireCount(railroads, 4, "鐵路");
    requireCount(utilities, 2, "公用事業");

    List<Tile> tiles = new ArrayList<>(Board.TILE_COUNT);

    // 整副棋盤共用同一份交易規則：格子只描述位置，收租時轉呼叫這個服務。
    PropertyMarket market = new PropertyMarket();

    // 街道組別：名稱、數量、每棟房屋（酒店）的建造費用。
    PropertyGroup brown     = new PropertyGroup("Brown", 2, 50);
    PropertyGroup lightBlue = new PropertyGroup("Light Blue", 3, 50);
    PropertyGroup pink      = new PropertyGroup("Pink", 3, 100);
    PropertyGroup orange    = new PropertyGroup("Orange", 3, 100);
    PropertyGroup red       = new PropertyGroup("Red", 3, 150);
    PropertyGroup yellow    = new PropertyGroup("Yellow", 3, 150);
    PropertyGroup green     = new PropertyGroup("Green", 3, 200);
    PropertyGroup darkBlue  = new PropertyGroup("Dark Blue", 2, 200);
    // 鐵路與公用事業不能建造，因此建造費用為 0。
    PropertyGroup railroadGroup = new PropertyGroup("Railroad", 4, 0);
    PropertyGroup utilityGroup  = new PropertyGroup("Utility", 2, 0);

    int streetIndex = 0;
    int railroadIndex = 0;
    int utilityIndex = 0;

    tiles.add(new GoTile(0, "GO"));
    tiles.add(new PropertyTile(1, new StreetProperty(streets[streetIndex++], 60, brown,
        new int[]{2, 10, 30, 90, 160, 250}), market));
    tiles.add(new CommunityChestTile(2, "Community Chest"));
    tiles.add(new PropertyTile(3, new StreetProperty(streets[streetIndex++], 60, brown,
        new int[]{4, 20, 60, 180, 320, 450}), market));
    tiles.add(new TaxTile(4, "Income Tax", 200));
    tiles.add(new PropertyTile(5, new RailroadProperty(railroads[railroadIndex++], 200,
        railroadGroup), market));
    tiles.add(new PropertyTile(6, new StreetProperty(streets[streetIndex++], 100, lightBlue,
        new int[]{6, 30, 90, 270, 400, 550}), market));
    tiles.add(new ChanceTile(7, "Chance"));
    tiles.add(new PropertyTile(8, new StreetProperty(streets[streetIndex++], 100, lightBlue,
        new int[]{6, 30, 90, 270, 400, 550}), market));
    tiles.add(new PropertyTile(9, new StreetProperty(streets[streetIndex++], 120, lightBlue,
        new int[]{8, 40, 100, 300, 450, 600}), market));
    tiles.add(new JailTile(10, "Jail / Just Visiting"));
    tiles.add(new PropertyTile(11, new StreetProperty(streets[streetIndex++], 140, pink,
        new int[]{10, 50, 150, 450, 625, 750}), market));
    tiles.add(new PropertyTile(12, new UtilityProperty(utilities[utilityIndex++], 150,
        utilityGroup), market));
    tiles.add(new PropertyTile(13, new StreetProperty(streets[streetIndex++], 140, pink,
        new int[]{10, 50, 150, 450, 625, 750}), market));
    tiles.add(new PropertyTile(14, new StreetProperty(streets[streetIndex++], 160, pink,
        new int[]{12, 60, 180, 500, 700, 900}), market));
    tiles.add(new PropertyTile(15, new RailroadProperty(railroads[railroadIndex++], 200,
        railroadGroup), market));
    tiles.add(new PropertyTile(16, new StreetProperty(streets[streetIndex++], 180, orange,
        new int[]{14, 70, 200, 550, 750, 950}), market));
    tiles.add(new CommunityChestTile(17, "Community Chest"));
    tiles.add(new PropertyTile(18, new StreetProperty(streets[streetIndex++], 180, orange,
        new int[]{14, 70, 200, 550, 750, 950}), market));
    tiles.add(new PropertyTile(19, new StreetProperty(streets[streetIndex++], 200, orange,
        new int[]{16, 80, 220, 600, 800, 1000}), market));
    tiles.add(new FreeParkingTile(20, "Free Parking"));
    tiles.add(new PropertyTile(21, new StreetProperty(streets[streetIndex++], 220, red,
        new int[]{18, 90, 250, 700, 875, 1050}), market));
    tiles.add(new ChanceTile(22, "Chance"));
    tiles.add(new PropertyTile(23, new StreetProperty(streets[streetIndex++], 220, red,
        new int[]{18, 90, 250, 700, 875, 1050}), market));
    tiles.add(new PropertyTile(24, new StreetProperty(streets[streetIndex++], 240, red,
        new int[]{20, 100, 300, 750, 925, 1100}), market));
    tiles.add(new PropertyTile(25, new RailroadProperty(railroads[railroadIndex++], 200,
        railroadGroup), market));
    tiles.add(new PropertyTile(26, new StreetProperty(streets[streetIndex++], 260, yellow,
        new int[]{22, 110, 330, 800, 975, 1150}), market));
    tiles.add(new PropertyTile(27, new StreetProperty(streets[streetIndex++], 260, yellow,
        new int[]{22, 110, 330, 800, 975, 1150}), market));
    tiles.add(new PropertyTile(28, new UtilityProperty(utilities[utilityIndex++], 150,
        utilityGroup), market));
    tiles.add(new PropertyTile(29, new StreetProperty(streets[streetIndex++], 280, yellow,
        new int[]{24, 120, 360, 850, 1025, 1200}), market));
    tiles.add(new GoToJailTile(30, "Go To Jail"));
    tiles.add(new PropertyTile(31, new StreetProperty(streets[streetIndex++], 300, green,
        new int[]{26, 130, 390, 900, 1100, 1275}), market));
    tiles.add(new PropertyTile(32, new StreetProperty(streets[streetIndex++], 300, green,
        new int[]{26, 130, 390, 900, 1100, 1275}), market));
    tiles.add(new CommunityChestTile(33, "Community Chest"));
    tiles.add(new PropertyTile(34, new StreetProperty(streets[streetIndex++], 320, green,
        new int[]{28, 150, 450, 1000, 1200, 1400}), market));
    tiles.add(new PropertyTile(35, new RailroadProperty(railroads[railroadIndex++], 200,
        railroadGroup), market));
    tiles.add(new ChanceTile(36, "Chance"));
    tiles.add(new PropertyTile(37, new StreetProperty(streets[streetIndex++], 350, darkBlue,
        new int[]{35, 175, 500, 1100, 1300, 1500}), market));
    tiles.add(new TaxTile(38, "Luxury Tax", 100));
    tiles.add(new PropertyTile(39, new StreetProperty(streets[streetIndex++], 400, darkBlue,
        new int[]{50, 200, 600, 1400, 1700, 2000}), market));

    return new Board(tiles);
  }

  /**
   * 檢查名稱表長度，避免呼叫端少給名稱時 quietly 建出錯誤的棋盤。
   *
   * @param names 名稱表
   * @param expected 期望數量
   * @param label 錯誤訊息用的類別名稱
   */
  private static void requireCount(String[] names, int expected, String label) {
    if (names.length != expected) {
      throw new IllegalArgumentException(label + "名稱必須剛好 " + expected + " 筆。");
    }
  }
}
