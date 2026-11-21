package monopoly.test;

import monopoly.InsufficientFundsException;
import monopoly.InvalidActionException;
import monopoly.Player;
import monopoly.board.Board;
import monopoly.board.BoardFactory;
import monopoly.board.tile.PropertyTile;
import monopoly.property.BuildingLevel;
import monopoly.property.Property;
import monopoly.property.PropertyDevelopment;
import monopoly.property.PropertyGroup;
import monopoly.property.PropertyMarket;
import monopoly.property.type.RailroadProperty;
import monopoly.property.type.StreetProperty;
import monopoly.property.type.UtilityProperty;

import java.util.List;

import static monopoly.test.TestSupport.check;
import static monopoly.test.TestSupport.checkEquals;
import static monopoly.test.TestSupport.expectThrows;
import static monopoly.test.TestSupport.scenario;

/**
 * 地產測試：租金、抵押、房屋與酒店規則。
 */
public final class PropertyTests {
  private PropertyTests() {
  }

  /** 執行全部地產測試。 */
  public static void run() {
    TestSupport.suite("地產：租金計算");
    rentTests();
    TestSupport.suite("地產：買賣與抵押");
    marketTests();
    TestSupport.suite("地產：房屋與酒店");
    buildingTests();
    TestSupport.suite("地產：交易復原（全成或全不成）");
    rollbackTests();
  }

  /** 取得棋盤上的指定地產。 */
  static Property propertyAt(Board board, int index) {
    return ((PropertyTile) board.getTile(index)).getProperty();
  }

  /** 街道租金、整組加倍、鐵路與公用事業。 */
  private static void rentTests() {
    scenario("街道租金依等級查表", () -> {
      Board board = BoardFactory.createClassicBoard();
      StreetProperty boardwalk = (StreetProperty) propertyAt(board, 39);
      StreetProperty parkPlace = (StreetProperty) propertyAt(board, 37);
      TestPlayer owner = new TestPlayer("地主", 20000);
      PropertyMarket market = new PropertyMarket();
      PropertyDevelopment development = new PropertyDevelopment();
      market.buy(owner, boardwalk);
      checkEquals(50, boardwalk.calculateRent(7), "未集滿整組時空地租金 50");

      // 集滿整組後，空地租金加倍；蓋上房屋後改查租金表。
      market.buy(owner, parkPlace);
      int[] expected = {100, 200, 600, 1400, 1700, 2000};
      for (int level = 0; level <= 5; level++) {
        checkEquals(expected[level], boardwalk.calculateRent(7),
            "Boardwalk 等級 " + level + " 的租金");
        if (level < 5) {
          // 平均建造：兩格必須同步升級，才不會超過 1 級差距。
          development.build(owner, boardwalk);
          development.build(owner, parkPlace);
        }
      }
      checkEquals(BuildingLevel.HOTEL, boardwalk.getBuildingLevel(), "蓋滿後應為酒店");
    });

    scenario("集滿整組時空地租金加倍", () -> {
      Board board = BoardFactory.createClassicBoard();
      TestPlayer owner = new TestPlayer("地主", 2000);
      Property mediterranean = propertyAt(board, 1);
      Property baltic = propertyAt(board, 3);

      PropertyMarket market = new PropertyMarket();
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);
      checkEquals(4, mediterranean.calculateRent(7), "集滿整組後加倍為 4 元");
      checkEquals(8, baltic.calculateRent(7), "Baltic 空地 4 元加倍為 8 元");

      // 蓋房屋後不再套用整組加倍（房屋租金已內含）。
      owner.gainMoney(1000);
      new PropertyDevelopment().build(owner, mediterranean);
      checkEquals(10, mediterranean.calculateRent(7), "1 棟房屋租金為 10 元，不再加倍");
    });

    scenario("鐵路租金依持有數量", () -> {
      Board board = BoardFactory.createClassicBoard();
      TestPlayer owner = new TestPlayer("車主", 2000);
      List<Property> railroads = board.getProperties().stream()
          .filter(p -> p instanceof RailroadProperty)
          .toList();
      checkEquals(4, railroads.size(), "應有 4 條鐵路");

      PropertyMarket market = new PropertyMarket();
      int[] expected = {25, 50, 100, 200};
      for (int i = 0; i < railroads.size(); i++) {
        market.buy(owner, railroads.get(i));
        checkEquals(expected[i], railroads.get(0).calculateRent(7),
            "擁有 " + (i + 1) + " 條鐵路時租金");
      }
    });

    scenario("公用事業租金為骰數 4 倍或 10 倍", () -> {
      Board board = BoardFactory.createClassicBoard();
      TestPlayer owner = new TestPlayer("業主", 2000);
      Property electric = board.findPropertyByName("Electric Company");
      Property water = board.findPropertyByName("Water Works");

      PropertyMarket market = new PropertyMarket();
      market.buy(owner, electric);
      checkEquals(20, electric.calculateRent(5), "只有 1 項時 5×4");
      checkEquals(60, electric.calculateRent(15), "只有 1 項時 15×4");

      market.buy(owner, water);
      checkEquals(50, electric.calculateRent(5), "擁有 2 項時 5×10");
      checkEquals(150, water.calculateRent(15), "擁有 2 項時 15×10");
    });

    scenario("無人持有或抵押中不收租", () -> {
      Board board = BoardFactory.createClassicBoard();
      Property boardwalk = propertyAt(board, 39);
      checkEquals(0, boardwalk.calculateRent(7), "無人持有時租金為 0");
      TestPlayer owner = new TestPlayer("地主", 2000);
      PropertyMarket market = new PropertyMarket();
      market.buy(owner, boardwalk);
      market.mortgage(owner, boardwalk);
      checkEquals(0, boardwalk.calculateRent(7), "抵押中租金為 0");
    });

    scenario("租金表供地契顯示且為副本", () -> {
      Board board = BoardFactory.createClassicBoard();

      // 街道：六級租金。
      Property boardwalk = propertyAt(board, 39);
      int[] streetTiers = boardwalk.getRentTiers();
      checkEquals(6, streetTiers.length, "街道租金表有 6 級");
      int[] expectedStreet = {50, 200, 600, 1400, 1700, 2000};
      for (int level = 0; level < expectedStreet.length; level++) {
        checkEquals(expectedStreet[level], streetTiers[level], "Boardwalk 第 " + level + " 級租金");
      }

      // 鐵路：持有 1～4 條的租金，不含「持有 0 條」。
      int[] railroadTiers = board.findPropertyByName("Reading Railroad").getRentTiers();
      checkEquals(4, railroadTiers.length, "鐵路租金表有 4 級");
      int[] expectedRailroad = {25, 50, 100, 200};
      for (int count = 0; count < expectedRailroad.length; count++) {
        checkEquals(expectedRailroad[count], railroadTiers[count],
            "持有 " + (count + 1) + " 條鐵路的租金");
      }

      // 公用事業：乘數倍率而非金額。
      int[] utilityTiers = board.findPropertyByName("Electric Company").getRentTiers();
      checkEquals(2, utilityTiers.length, "公用事業倍率表有 2 級");
      checkEquals(4, utilityTiers[0], "持有 1 項時為骰數 ×4");
      checkEquals(10, utilityTiers[1], "持有 2 項時為骰數 ×10");

      // 回傳副本：改動不影響地產本身。
      streetTiers[0] = 999_999;
      checkEquals(50, boardwalk.getRentTiers()[0], "修改回傳陣列不影響地產");
    });
  }

  /** 買賣、抵押與贖回。 */
  private static void marketTests() {
    scenario("購買成功後轉移所有權並扣款", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer buyer = new TestPlayer("買家", 1500);

      check(market.canBuy(buyer, boardwalk), "現金足夠時可以購買");
      int paid = market.buy(buyer, boardwalk);
      checkEquals(400, paid, "Boardwalk 價格為 400");
      checkEquals(1100, buyer.getBalance(), "扣款後餘額 1100");
      checkEquals(buyer, boardwalk.getOwner(), "所有權轉移給買家");
      checkEquals(1, buyer.getOwnedProperties().size(), "買家的持有清單多了一筆");
      check(buyer.getOwnedProperties().contains(boardwalk), "持有清單內容是剛買的地產");
    });

    scenario("現金不足時不扣款也不轉移", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer poor = new TestPlayer("窮人", 100);

      check(!market.canBuy(poor, boardwalk), "現金不足時不可購買");
      expectThrows(InsufficientFundsException.class,
          () -> market.buy(poor, boardwalk), "現金不足時購買失敗");
      checkEquals(100, poor.getBalance(), "失敗後餘額不變");
      check(!boardwalk.isOwned(), "失敗後仍無人持有");
      check(poor.getOwnedProperties().isEmpty(), "失敗後持有清單仍為空");
    });

    scenario("已售出的地產不能再買", () -> {      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer first = new TestPlayer("第一位", 1500);
      TestPlayer second = new TestPlayer("第二位", 1500);
      market.buy(first, boardwalk);

      check(!market.canBuy(second, boardwalk), "已售出時不可購買");
      expectThrows(InvalidActionException.class,
          () -> market.buy(second, boardwalk), "重複購買應失敗");
      checkEquals(1500, second.getBalance(), "失敗方餘額不變");
    });

    scenario("抵押與贖回的現金流", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer owner = new TestPlayer("地主", 1000);
      market.buy(owner, boardwalk);
      checkEquals(600, owner.getBalance(), "買完剩 600");

      check(market.canMortgage(owner, boardwalk), "無建築時可以抵押");
      int received = market.mortgage(owner, boardwalk);
      checkEquals(200, received, "抵押可得價格一半 200");
      checkEquals(800, owner.getBalance(), "抵押後餘額 800");
      check(boardwalk.isMortgaged(), "地產應為抵押狀態");

      expectThrows(InvalidActionException.class,
          () -> market.mortgage(owner, boardwalk), "重複抵押應失敗");

      check(market.canUnmortgage(owner, boardwalk), "抵押中可贖回");
      int cost = market.unmortgage(owner, boardwalk);
      checkEquals(220, cost, "贖回需 200 + 10% 利息 = 220");
      checkEquals(580, owner.getBalance(), "贖回後餘額 580");
      check(!boardwalk.isMortgaged(), "贖回後取消抵押");
      check(owner.getOwnedProperties().contains(boardwalk), "抵押與贖回都不影響持有清單");
    });

    scenario("非持有人不能抵押或贖回", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer owner = new TestPlayer("地主", 1500);
      TestPlayer other = new TestPlayer("路人", 1500);
      market.buy(owner, boardwalk);

      expectThrows(InvalidActionException.class,
          () -> market.mortgage(other, boardwalk), "非持有人不能抵押");
      market.mortgage(owner, boardwalk);
      expectThrows(InvalidActionException.class,
          () -> market.unmortgage(other, boardwalk), "非持有人不能贖回");
    });

    scenario("收租：付款方扣款、地主入帳", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer owner = new TestPlayer("地主", 1500);
      TestPlayer tenant = new TestPlayer("租客", 1000);
      market.buy(owner, boardwalk);

      int rent = market.payRent(tenant, boardwalk, 7);
      checkEquals(50, rent, "空地租金 50");
      checkEquals(950, tenant.getBalance(), "租客扣 50");
      checkEquals(1150, owner.getBalance(), "地主由 1100 增加到 1150");

      checkEquals(0, market.payRent(owner, boardwalk, 7), "地主自己踩到不收租");
    });

    scenario("租客現金不足時不收租也不扣款", () -> {
      Board board = BoardFactory.createClassicBoard();
      Property boardwalk = propertyAt(board, 39);
      PropertyMarket market = new PropertyMarket();
      TestPlayer owner = new TestPlayer("地主", 1500);
      TestPlayer tenant = new TestPlayer("租客", 10);
      market.buy(owner, boardwalk);

      expectThrows(InsufficientFundsException.class,
          () -> market.payRent(tenant, boardwalk, 7), "租金付不出時應失敗");
      checkEquals(10, tenant.getBalance(), "租客餘額不變");
      checkEquals(1100, owner.getBalance(), "地主未收到租金");
    });

    scenario("賣回銀行取回半價", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer owner = new TestPlayer("地主", 1500);
      market.buy(owner, boardwalk);
      checkEquals(1100, owner.getBalance(), "買完剩 1100");

      check(market.canSellToBank(owner, boardwalk), "無建築時可以賣回銀行");
      int refund = market.sellToBank(owner, boardwalk);
      checkEquals(200, refund, "取回購買價一半 200");
      checkEquals(1300, owner.getBalance(), "賣出後餘額 1300");
      check(!boardwalk.isOwned(), "賣出後回到無人持有");
      checkEquals(0, boardwalk.calculateRent(7), "無人持有時不再收租");
      check(owner.getOwnedProperties().isEmpty(), "賣出後持有清單已清空");
    });

    scenario("賣回銀行：非持有人與有建築時失敗", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      PropertyDevelopment development = new PropertyDevelopment();
      TestPlayer owner = new TestPlayer("地主", 5000);
      TestPlayer other = new TestPlayer("路人", 5000);
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);

      expectThrows(InvalidActionException.class,
          () -> market.sellToBank(other, mediterranean), "非持有人不能賣回銀行");

      development.build(owner, mediterranean);
      check(!market.canSellToBank(owner, mediterranean), "有房屋時不可賣回銀行");
      expectThrows(InvalidActionException.class,
          () -> market.sellToBank(owner, mediterranean), "有建築時賣回應失敗");
      checkEquals(owner, mediterranean.getOwner(), "失敗後仍持有");

      // 拆屋後即可賣回銀行。
      development.sellBuilding(owner, mediterranean);
      check(market.canSellToBank(owner, mediterranean), "拆屋後可以賣回銀行");
    });

    scenario("抵押中的地產賣回銀行後解除抵押", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer owner = new TestPlayer("地主", 1500);
      market.buy(owner, boardwalk);
      market.mortgage(owner, boardwalk);
      check(boardwalk.isMortgaged(), "已抵押");

      market.sellToBank(owner, boardwalk);
      check(!boardwalk.isMortgaged(), "賣回銀行後不再抵押");
      check(!boardwalk.isOwned(), "賣回銀行後無人持有");
    });
  }

  /** 房屋與酒店的建造規則。 */
  private static void buildingTests() {
    scenario("未集滿整組不能建造", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      PropertyMarket market = new PropertyMarket();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      TestPlayer owner = new TestPlayer("地主", 2000);
      market.buy(owner, mediterranean);

      check(!development.canBuild(owner, mediterranean), "只有 1 筆時不可建造");
      expectThrows(InvalidActionException.class,
          () -> development.build(owner, mediterranean), "未集滿整組時建造失敗");
      checkEquals(1940, owner.getBalance(), "失敗後餘額不變");
    });

    scenario("平均建造：不能只把一格蓋到頂", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      PropertyMarket market = new PropertyMarket();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      TestPlayer owner = new TestPlayer("地主", 2000);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);

      check(development.canBuild(owner, mediterranean), "集滿後可以開始建造");
      development.build(owner, mediterranean);
      checkEquals(BuildingLevel.HOUSE_1, mediterranean.getBuildingLevel(), "第一棟房屋");
      check(!development.canBuild(owner, mediterranean), "另一格等級較低時不可續建");
      expectThrows(InvalidActionException.class,
          () -> development.build(owner, mediterranean), "違反平均建造應失敗");

      development.build(owner, baltic);
      checkEquals(BuildingLevel.HOUSE_1, baltic.getBuildingLevel(), "兩格同為 1 棟");
      check(development.canBuild(owner, mediterranean), "兩格同級後可以繼續建造");
    });

    scenario("建造費用由組別決定並逐級扣除", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      PropertyMarket market = new PropertyMarket();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      TestPlayer owner = new TestPlayer("地主", 2000);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);
      checkEquals(1880, owner.getBalance(), "買兩筆後的餘額");

      int cost = development.build(owner, mediterranean);
      checkEquals(50, cost, "Brown 組每棟 50 元");
      checkEquals(1830, owner.getBalance(), "扣除建造費後餘額");
    });

    scenario("蓋到酒店後不能再建", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      PropertyMarket market = new PropertyMarket();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      TestPlayer owner = new TestPlayer("地主", 5000);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);

      for (int level = 1; level <= 5; level++) {
        development.build(owner, mediterranean);
        development.build(owner, baltic);
      }
      checkEquals(BuildingLevel.HOTEL, mediterranean.getBuildingLevel(), "地中海大道應為酒店");
      checkEquals(BuildingLevel.HOTEL, baltic.getBuildingLevel(), "波羅的海大道應為酒店");
      check(!development.canBuild(owner, mediterranean), "酒店後不可再建");
      expectThrows(InvalidActionException.class,
          () -> development.build(owner, mediterranean), "超過酒店等級應失敗");
    });

    scenario("拆除退回半數建造費並遵守平均拆除", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      PropertyMarket market = new PropertyMarket();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      TestPlayer owner = new TestPlayer("地主", 5000);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);
      development.build(owner, mediterranean);
      development.build(owner, baltic);
      development.build(owner, mediterranean);

      int balanceBefore = owner.getBalance();
      check(!development.canSellBuilding(owner, baltic), "等級較低的格子不能先拆");
      expectThrows(InvalidActionException.class,
          () -> development.sellBuilding(owner, baltic), "違反平均拆除應失敗");
      check(development.canSellBuilding(owner, mediterranean), "等級最高的格子可以拆除");
      int refund = development.sellBuilding(owner, mediterranean);
      checkEquals(25, refund, "退回建造費一半 25 元");
      checkEquals(balanceBefore + 25, owner.getBalance(), "退款入帳");
      checkEquals(BuildingLevel.HOUSE_1, mediterranean.getBuildingLevel(), "退回後剩 1 棟");

      // 兩格同為 1 棟時，任一格都可以拆（拆完差距仍不超過 1）。
      check(development.canSellBuilding(owner, baltic), "同級時可以拆除");
      development.sellBuilding(owner, baltic);
      checkEquals(BuildingLevel.NONE, baltic.getBuildingLevel(), "拆除後該格無建築");
      checkEquals(BuildingLevel.HOUSE_1, mediterranean.getBuildingLevel(), "另一格仍為 1 棟");
    });

    scenario("建築總數統計", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      PropertyMarket market = new PropertyMarket();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      TestPlayer owner = new TestPlayer("地主", 5000);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);
      development.build(owner, mediterranean);
      development.build(owner, baltic);
      development.build(owner, mediterranean);

      PropertyGroup brown = mediterranean.getGroup();
      checkEquals(3, development.countBuildings(owner, brown), "整組共 3 級建築");
    });

    scenario("有建築時不能抵押", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      PropertyMarket market = new PropertyMarket();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      TestPlayer owner = new TestPlayer("地主", 5000);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);
      development.build(owner, mediterranean);

      check(!market.canMortgage(owner, mediterranean), "有房屋時不可抵押");
      expectThrows(InvalidActionException.class,
          () -> market.mortgage(owner, mediterranean), "有建築時抵押應失敗");

      // 拆掉房屋後即可抵押。
      development.sellBuilding(owner, mediterranean);
      check(market.canMortgage(owner, mediterranean), "拆屋後可以抵押");
    });

    scenario("組內有地產抵押時整組不能建造", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      PropertyMarket market = new PropertyMarket();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      TestPlayer owner = new TestPlayer("地主", 5000);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);
      market.mortgage(owner, baltic);

      check(!development.canBuild(owner, mediterranean), "組內有抵押時不可建造");
      expectThrows(InvalidActionException.class,
          () -> development.build(owner, mediterranean), "抵押期間建造應失敗");
    });

    scenario("鐵路與公用事業不能建造", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyDevelopment development = new PropertyDevelopment();
      Property railroad = board.findPropertyByName("Reading Railroad");
      Property utility = board.findPropertyByName("Electric Company");
      TestPlayer owner = new TestPlayer("地主", 5000);
      PropertyMarket market = new PropertyMarket();
      market.buy(owner, railroad);
      market.buy(owner, utility);

      check(!development.canBuild(owner, railroad), "鐵路不可建造");
      check(!development.canBuild(owner, utility), "公用事業不可建造");
      expectThrows(InvalidActionException.class,
          () -> development.build(owner, railroad), "鐵路建造應失敗");
      expectThrows(InvalidActionException.class,
          () -> development.build(owner, utility), "公用事業建造應失敗");
      expectThrows(InvalidActionException.class,
          () -> development.sellBuilding(owner, railroad), "鐵路沒有建築可拆");
    });
  }

  /**
   * 交易中段失敗時的復原：任何一步拋例外，整體都必須回到交易前的狀態。
   *
   * <p>用 {@link TestPlayer} 的故障注入模擬「扣款／入帳／資產清單操作」失敗，
   * 驗證 {@link PropertyMarket} 與 {@link PropertyDevelopment} 的全成或全不成。
   */
  private static void rollbackTests() {
    scenario("購買時加入資產清單失敗 → 所有權與現金都還原", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer buyer = new TestPlayer("買家", 1500);
      buyer.failNextAddProperty(true);

      expectThrows(IllegalStateException.class,
          () -> market.buy(buyer, boardwalk), "加入資產清單失敗會往外拋");
      checkEquals(null, boardwalk.getOwner(), "地產回到無人持有");
      checkEquals(1500, buyer.getBalance(), "買家餘額未變");
      checkEquals(0, buyer.getOwnedProperties().size(), "買家資產清單為空");
    });

    scenario("購買時扣款失敗 → 所有權還原", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer buyer = new TestPlayer("買家", 1500);
      buyer.failNextDecMoney(true);

      expectThrows(IllegalStateException.class,
          () -> market.buy(buyer, boardwalk), "扣款失敗會往外拋");
      checkEquals(null, boardwalk.getOwner(), "地產回到無人持有");
      checkEquals(1500, buyer.getBalance(), "買家餘額未變");
      checkEquals(0, buyer.getOwnedProperties().size(), "買家資產清單為空");
    });

    scenario("收租時入帳失敗 → 租金退還付款方", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer owner = new TestPlayer("地主", 5000);
      TestPlayer payer = new TestPlayer("租客", 5000);
      market.buy(owner, boardwalk);
      int ownerBalance = owner.getBalance();

      owner.failNextGainMoney(true);
      expectThrows(IllegalStateException.class,
          () -> market.payRent(payer, boardwalk, 7), "入帳失敗會往外拋");
      checkEquals(5000, payer.getBalance(), "付款方租金已退還");
      checkEquals(ownerBalance, owner.getBalance(), "地主餘額未變");
    });

    scenario("抵押時入帳失敗 → 抵押狀態還原", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer owner = new TestPlayer("地主", 1500);
      market.buy(owner, boardwalk);
      int ownerBalance = owner.getBalance();

      owner.failNextGainMoney(true);
      expectThrows(IllegalStateException.class,
          () -> market.mortgage(owner, boardwalk), "入帳失敗會往外拋");
      check(!boardwalk.isMortgaged(), "地產未變成抵押中");
      checkEquals(ownerBalance, owner.getBalance(), "地主餘額未變");
    });

    scenario("賣回銀行時入帳失敗 → 持有人與抵押狀態都還原", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      Property boardwalk = propertyAt(board, 39);
      TestPlayer owner = new TestPlayer("地主", 1500);
      market.buy(owner, boardwalk);
      market.mortgage(owner, boardwalk);
      int ownerBalance = owner.getBalance();

      owner.failNextGainMoney(true);
      expectThrows(IllegalStateException.class,
          () -> market.sellToBank(owner, boardwalk), "入帳失敗會往外拋");
      checkEquals(owner, boardwalk.getOwner(), "持有人已還原");
      check(boardwalk.isMortgaged(), "抵押狀態已還原");
      checkEquals(ownerBalance, owner.getBalance(), "地主餘額未變");
      check(owner.getOwnedProperties().contains(boardwalk), "資產清單已還原");
    });

    scenario("建造時付款失敗 → 建築等級還原", () -> {
      Board board = BoardFactory.createClassicBoard();
      PropertyMarket market = new PropertyMarket();
      PropertyDevelopment development = new PropertyDevelopment();
      StreetProperty mediterranean = (StreetProperty) propertyAt(board, 1);
      StreetProperty baltic = (StreetProperty) propertyAt(board, 3);
      TestPlayer owner = new TestPlayer("地主", 5000);
      market.buy(owner, mediterranean);
      market.buy(owner, baltic);
      int ownerBalance = owner.getBalance();

      owner.failNextDecMoney(true);
      expectThrows(IllegalStateException.class,
          () -> development.build(owner, mediterranean), "付款失敗會往外拋");
      checkEquals(BuildingLevel.NONE, mediterranean.getBuildingLevel(), "建築等級還原為空地");
      checkEquals(ownerBalance, owner.getBalance(), "地主餘額未變");
      checkEquals(0, development.countBuildings(owner, mediterranean.getGroup()),
          "整組建築數為 0");
    });
  }
}
