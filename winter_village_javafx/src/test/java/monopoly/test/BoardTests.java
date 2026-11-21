package monopoly.test;

import monopoly.board.Board;
import monopoly.board.BoardFactory;
import monopoly.board.tile.ChanceTile;
import monopoly.board.tile.CommunityChestTile;
import monopoly.board.tile.FreeParkingTile;
import monopoly.board.tile.GoTile;
import monopoly.board.tile.GoToJailTile;
import monopoly.board.tile.JailTile;
import monopoly.board.tile.PropertyTile;
import monopoly.board.tile.TaxTile;
import monopoly.board.tile.Tile;
import monopoly.property.Property;
import monopoly.property.PropertyDevelopment;
import monopoly.property.PropertyMarket;
import monopoly.property.type.StreetProperty;

import java.util.List;

import static monopoly.test.TestSupport.check;
import static monopoly.test.TestSupport.checkEquals;
import static monopoly.test.TestSupport.expectThrows;
import static monopoly.test.TestSupport.scenario;

/**
 * 棋盤測試：40 格結構、格子查詢、移動與繞過 GO。
 */
public final class BoardTests {
  private BoardTests() {
  }

  /** 執行全部棋盤測試。 */
  public static void run() {
    TestSupport.suite("棋盤：40 格與移動");

    scenario("棋盤固定 40 格", () -> {
      Board board = BoardFactory.createClassicBoard();
      checkEquals(40, board.size(), "棋盤應有 40 格");
      checkEquals(40, board.getTiles().size(), "格子清單應有 40 筆");
      for (int index = 0; index < 40; index++) {
        checkEquals(index, board.getTile(index).getIndex(), "第 " + index + " 格的索引");
      }
    });

    scenario("關鍵格子的位置與類型", () -> {
      Board board = BoardFactory.createClassicBoard();
      check(board.getTile(0) instanceof GoTile, "0 應為起點格 GO");
      check(board.getTile(2) instanceof CommunityChestTile, "2 應為社區福利格");
      check(board.getTile(4) instanceof TaxTile, "4 應為所得稅格");
      check(board.getTile(7) instanceof ChanceTile, "7 應為機會格");
      check(board.getTile(10) instanceof JailTile, "10 應為監獄格");
      check(board.getTile(20) instanceof FreeParkingTile, "20 應為免費停車");
      check(board.getTile(30) instanceof GoToJailTile, "30 應為進監獄");
      check(board.getTile(39) instanceof PropertyTile, "39 應為地產格 Boardwalk");
      checkEquals("Boardwalk", board.getTile(39).getName(), "第 39 格名稱");
    });

    scenario("地產數量與種類", () -> {
      Board board = BoardFactory.createClassicBoard();
      List<Property> properties = board.getProperties();
      checkEquals(28, properties.size(), "經典棋盤共有 28 筆可購買地產");
      long railroads = properties.stream()
          .filter(p -> p instanceof monopoly.property.type.RailroadProperty)
          .count();
      checkEquals(4L, railroads, "應有 4 條鐵路");
      long utilities = properties.stream()
          .filter(p -> p instanceof monopoly.property.type.UtilityProperty)
          .count();
      checkEquals(2L, utilities, "應有 2 項公用事業");
      checkEquals(22L, properties.size() - railroads - utilities, "街道應有 22 條");
    });

    scenario("依名稱與地產查格子", () -> {
      Board board = BoardFactory.createClassicBoard();
      Tile tile = board.findTile("Boardwalk");
      checkEquals(39, tile.getIndex(), "Boardwalk 在第 39 格");
      Property boardwalk = ((PropertyTile) tile).getProperty();
      checkEquals(39, board.indexOf(boardwalk), "indexOf 應回傳 39");
      expectThrows(IllegalArgumentException.class,
          () -> board.findTile("不存在的格子"), "找不到名稱時應拋例外");
    });

    scenario("依索引取得該格地產", () -> {
      Board board = BoardFactory.createWinterVillageBoard();
      checkEquals(null, board.getPropertyAt(0), "GO 不是地產格，回傳 null");
      checkEquals(null, board.getPropertyAt(7), "機會不是地產格，回傳 null");
      checkEquals("Frozen Brook Trail", board.getPropertyAt(1).getName(), "第 1 格的地產");
      checkEquals("Meltwater Works", board.getPropertyAt(28).getName(), "第 28 格的地產");
      Property citadel = board.getPropertyAt(39);
      checkEquals(400, citadel.getPurchasePrice(), "第 39 格價格 400");
      expectThrows(IndexOutOfBoundsException.class,
          () -> board.getPropertyAt(40), "索引 40 超出範圍");
    });

    scenario("前進與繞回", () -> {
      Board board = BoardFactory.createClassicBoard();
      checkEquals(5, board.move(0, 5), "從 GO 前進 5 格");
      checkEquals(39, board.move(35, 4), "35 + 4 = 39");
      checkEquals(2, board.move(35, 7), "35 + 7 應繞回 2");
      checkEquals(0, board.move(39, 1), "39 + 1 應回到 0");
      checkEquals(0, board.move(0, 40), "前進 40 格應回到原點");
      checkEquals(38, board.moveBack(1, 3), "從 1 後退 3 格應繞回 38");
    });

    scenario("是否經過 GO", () -> {
      Board board = BoardFactory.createClassicBoard();
      check(!board.passesGo(0, 5), "從 GO 前進 5 格尚未繞回");
      check(board.passesGo(35, 7), "35 + 7 會經過 GO");
      check(board.passesGo(39, 1), "39 + 1 停在 GO 也算經過");
      check(!board.passesGo(10, 0), "步數為 0 不算經過");
    });

    scenario("非法參數", () -> {
      Board board = BoardFactory.createClassicBoard();
      expectThrows(IndexOutOfBoundsException.class,
          () -> board.getTile(40), "索引 40 超出範圍");
      expectThrows(IndexOutOfBoundsException.class,
          () -> board.getTile(-1), "索引 -1 超出範圍");
      expectThrows(IllegalArgumentException.class,
          () -> board.move(0, -1), "前進步數不可為負數");
    });

    scenario("建構子檢查格子順序", () -> {
      Board source = BoardFactory.createClassicBoard();
      List<Tile> wrongOrder = new java.util.ArrayList<>(source.getTiles());
      java.util.Collections.swap(wrongOrder, 0, 1);
      expectThrows(IllegalArgumentException.class,
          () -> new Board(wrongOrder), "格子順序錯誤時應被拒絕");
      expectThrows(IllegalArgumentException.class,
          () -> new Board(source.getTiles().subList(0, 39)), "少於 40 格應被拒絕");
    });

    scenario("冬季村莊棋盤與主程式同名", () -> {
      Board winter = BoardFactory.createWinterVillageBoard();
      checkEquals(40, winter.size(), "冬季村莊棋盤同為 40 格");

      // 與主程式 setupWinterBoardData() 的主題名稱逐格比對。
      String[][] expectedNames = {
          { "1", "Frozen Brook Trail" }, { "3", "Old Mill Cabin" },
          { "6", "Icicle Hollow" }, { "8", "Snowdrift Lane" }, { "9", "Crystal Cavern" },
          { "11", "Aurora Trail" }, { "13", "Frosty Pines" }, { "14", "Winterberry Way" },
          { "16", "Fireside Lodge" }, { "18", "Cozy Cabin" }, { "19", "Hearthstone Inn" },
          { "21", "Cardinal Peak" }, { "23", "Redwood Retreat" }, { "24", "Ruby Ridge" },
          { "26", "Sunlit Ridge" }, { "27", "Golden Glade" }, { "29", "Sunshine Summit" },
          { "31", "Evergreen Pine" }, { "32", "Whispering Woods" }, { "34", "Forest Haven" },
          { "37", "Glacier Palace" }, { "39", "Winter Summit Citadel" },
          { "5", "North Star Station" }, { "15", "Snowy Express" },
          { "25", "Polar Express" }, { "35", "Glacier Express" },
          { "12", "Alpine Power" }, { "28", "Meltwater Works" },
      };
      for (String[] pair : expectedNames) {
        int index = Integer.parseInt(pair[0]);
        checkEquals(pair[1], winter.getTile(index).getName(), "第 " + index + " 格的冬季村莊名稱");
      }
    });

    scenario("兩種名稱表的價格、租金與組別一致", () -> {
      Board classic = BoardFactory.createClassicBoard();
      Board winter = BoardFactory.createWinterVillageBoard();
      for (int index = 0; index < Board.TILE_COUNT; index++) {
        Tile classicTile = classic.getTile(index);
        Tile winterTile = winter.getTile(index);
        check(classicTile.getClass() == winterTile.getClass(), "第 " + index + " 格的格子型別相同");
        if (!(classicTile instanceof PropertyTile) || !(winterTile instanceof PropertyTile)) {
          continue;
        }
        Property expected = ((PropertyTile) classicTile).getProperty();
        Property actual = ((PropertyTile) winterTile).getProperty();
        checkEquals(expected.getPurchasePrice(), actual.getPurchasePrice(),
            "第 " + index + " 格的價格相同");
        checkEquals(expected.getMortgageValue(), actual.getMortgageValue(),
            "第 " + index + " 格的抵押金相同");
        checkEquals(expected.getGroup().getRequiredProperties(),
            actual.getGroup().getRequiredProperties(), "第 " + index + " 格的組別大小相同");
        checkEquals(expected.getGroup().getBuildingCost(),
            actual.getGroup().getBuildingCost(), "第 " + index + " 格的建造費用相同");
        if (expected instanceof StreetProperty classicStreet
            && actual instanceof StreetProperty winterStreet) {
          // 街道的六級租金表必須逐級相同。
          for (int level = 0; level <= 5; level++) {
            checkEquals(classicStreet.getRentAt(level), winterStreet.getRentAt(level),
                "第 " + index + " 格等級 " + level + " 的租金相同");
          }
        }
      }
    });

    scenario("冬季村莊棋盤的租金規則與經典棋盤一致", () -> {
      Board winter = BoardFactory.createWinterVillageBoard();
      StreetProperty street = (StreetProperty) ((PropertyTile) winter.getTile(39)).getProperty();
      StreetProperty other = (StreetProperty) ((PropertyTile) winter.getTile(37)).getProperty();
      TestPlayer owner = new TestPlayer("地主", 5000);
      PropertyMarket market = new PropertyMarket();
      PropertyDevelopment development = new PropertyDevelopment();
      market.buy(owner, street);
      checkEquals(50, street.calculateRent(7), "未集滿整組時為租金表原價 50");

      market.buy(owner, other);
      checkEquals(100, street.calculateRent(7), "集滿 Frostbite Summit 後空地租金加倍為 100");

      // 平均建造：兩格同步升級後租金才會進入下一級。
      development.build(owner, street);
      development.build(owner, other);
      checkEquals(200, street.calculateRent(7), "1 棟房屋租金為 200，不再加倍");
      checkEquals("Winter Summit Citadel", street.getName(), "主題名稱仍保留");

      Property railroad = ((PropertyTile) winter.getTile(5)).getProperty();
      market.buy(owner, railroad);
      checkEquals(25, railroad.calculateRent(7), "1 條鐵路租金 25");

      Property utility = ((PropertyTile) winter.getTile(12)).getProperty();
      market.buy(owner, utility);
      checkEquals(28, utility.calculateRent(7), "1 項公用事業為骰數 ×4");
    });

    scenario("起點租金與抵押金額", () -> {
      Board board = BoardFactory.createClassicBoard();
      Property mediterranean = ((PropertyTile) board.getTile(1)).getProperty();
      checkEquals(60, mediterranean.getPurchasePrice(), "Mediterranean 價格為 60");
      checkEquals(30, mediterranean.getMortgageValue(), "抵押金為價格一半");
      checkEquals(33, mediterranean.getUnmortgageCost(), "贖回需 30 + 3 元利息");
      checkEquals(3, mediterranean.getMortgageInterest(), "利息為 3 元");
    });
  }
}
