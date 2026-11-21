package monopoly.property;

import monopoly.BankruptcyException;
import monopoly.InsufficientFundsException;
import monopoly.InvalidActionException;
import monopoly.Player;

import java.util.Objects;

/**
 * 地產交易服務：買賣、抵押、贖回與收租。
 *
 * <p>這個服務是「地產」唯一會動到玩家金錢的地方，所有規則檢查都集中在這裡，
 * 因此 UI 只要呼叫這幾個方法，就不會出現「先扣錢才發現不能買」之類的不一致。
 *
 * <p>金錢沿用 {@link Player#decMoney(int)} / {@link Player#gainMoney(int)} 的整數餘額；
 * 若之後要改用實體鈔票，只要把 {@code Player} 的實作接到
 * {@code monopoly.economy.CashTransaction} 即可，這個服務不需要改。
 *
 * <p>租金規則（依官方）：
 * <ul>
 *   <li>街道：依房屋／酒店等級查表；空地且車主集滿整組時加倍；</li>
 *   <li>鐵路：依車主擁有的鐵路數 25／50／100／200；</li>
 *   <li>公用事業：骰數 ×4，擁有兩項時 ×10；</li>
 *   <li>抵押中、無人持有、地主自己踩到 → 不收租。</li>
 * </ul>
 *
 * <p><b>每一筆交易都是「全成或全不成」</b>：所有檢查在動任何狀態之前做完，
 * 狀態改動過程中若後半段失敗（例如玩家實作在更新資產清單時拋例外），
 * 前面的改動會一併還原。呼叫端因此不需要自己補救半套狀態。
 */
public final class PropertyMarket {

  /** 建立無狀態的交易服務。 */
  public PropertyMarket() {
  }

  /**
   * 判斷玩家現在能否購買此地產。
   *
   * @param buyer 想購買的玩家
   * @param property 目標地產
   * @return 地產尚未售出、未抵押，且玩家現金足夠時回傳 true
   */
  public boolean canBuy(Player buyer, Property property) {
    Objects.requireNonNull(buyer, "buyer");
    Objects.requireNonNull(property, "property");
    return !property.isOwned()
        && !property.isMortgaged()
        && !buyer.isBankrupt()
        && buyer.getBalance() >= property.getPurchasePrice();
  }

  /**
   * 購買地產：先扣款，成功後才轉移所有權。
   *
   * @param buyer 買家
   * @param property 目標地產
   * @return 實際付出的金額（等於購買價）
   * @throws InvalidActionException 地產已售出、已抵押，或買家已破產
   * @throws InsufficientFundsException 現金不足（此時不會扣款）
   * @throws BankruptcyException 玩家實作判定為破產時
   */
  public int buy(Player buyer, Property property)
      throws InvalidActionException, InsufficientFundsException, BankruptcyException {
    Objects.requireNonNull(buyer, "buyer");
    Objects.requireNonNull(property, "property");
    if (property.isOwned()) {
      throw new InvalidActionException(
          property.getName() + " 已由 " + property.getOwner().getName() + " 持有。");
    }
    if (property.isMortgaged()) {
      throw new InvalidActionException(property.getName() + " 處於抵押狀態，不能購買。");
    }
    if (buyer.isBankrupt()) {
      throw new InvalidActionException(buyer.getName() + " 已破產，不能再購買地產。");
    }

    int price = property.getPurchasePrice();
    if (buyer.getBalance() < price) {
      throw new InsufficientFundsException(price, buyer.getBalance());
    }
    // 先把所有狀態改完，再扣款；扣款失敗就全部還原。
    PropertyState state = stateOf(property);
    state.changeOwner(buyer);
    try {
      buyer.addProperty(property);
    } catch (RuntimeException failure) {
      state.changeOwner(null);
      throw failure;
    }
    try {
      buyer.decMoney(price);
    } catch (InsufficientFundsException | BankruptcyException | RuntimeException failure) {
      buyer.removeProperty(property);
      state.changeOwner(null);
      throw failure;
    }
    return price;
  }

  /**
   * 收取租金：付款方付錢給地產持有人。
   *
   * <p>無人持有、地主自己踩到、地產抵押中時不收取，回傳 0。
   *
   * @param payer 踩到地產的玩家
   * @param property 踩到的地產
   * @param diceTotal 兩顆骰子的點數和（公用事業會用到）
   * @return 實際支付的租金；不需要付款時為 0
   * @throws InsufficientFundsException 付款方現金不足（此時不會扣款，規則層可改走抵押或破產流程）
   * @throws BankruptcyException 玩家實作判定為破產時
   */
  public int payRent(Player payer, Property property, int diceTotal)
      throws InsufficientFundsException, BankruptcyException {
    Objects.requireNonNull(payer, "payer");
    Objects.requireNonNull(property, "property");
    if (diceTotal < 0) {
      throw new IllegalArgumentException("骰子點數不可為負數。");
    }
    Player owner = property.getOwner();
    if (owner == null || owner.equals(payer) || property.isMortgaged()) {
      return 0;
    }

    int rent = property.calculateRent(diceTotal);
    if (rent <= 0) {
      return 0;
    }
    // 先扣款再入帳；入帳失敗時把租金還給付款方，避免錢憑空消失。
    payer.decMoney(rent);
    try {
      owner.gainMoney(rent);
    } catch (RuntimeException failure) {
      payer.gainMoney(rent);
      throw failure;
    }
    return rent;
  }

  /**
   * 判斷玩家現在能否抵押此地產。
   *
   * <p>官方規則：地產上有房屋或酒店時不能抵押，必須先拆除建築；
   * 因此街道必須先透過 {@link PropertyDevelopment#sellBuilding} 清空。
   *
   * @param owner 持有人
   * @param property 目標地產
   */
  public boolean canMortgage(Player owner, Property property) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    return property.isOwnedBy(owner)
        && !property.isMortgaged()
        && !hasBuildings(property);
  }

  /**
   * 抵押地產，向銀行換取現金（購買價的一半）。
   *
   * @param owner 持有人
   * @param property 目標地產
   * @return 取得的金額
   * @throws InvalidActionException 不是持有人、已抵押，或地產上還有建築
   */
  public int mortgage(Player owner, Property property) throws InvalidActionException {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    if (!property.isOwnedBy(owner)) {
      throw new InvalidActionException("只有持有人可以抵押 " + property.getName() + "。");
    }
    if (property.isMortgaged()) {
      throw new InvalidActionException(property.getName() + " 已經抵押。");
    }
    if (hasBuildings(property)) {
      throw new InvalidActionException(property.getName() + " 上還有建築，必須先拆除。");
    }

    int value = property.getMortgageValue();
    PropertyState state = stateOf(property);
    // 先標記抵押再入帳；入帳失敗就把抵押狀態還原，避免「抵押了卻沒拿到錢」。
    state.changeMortgageState(true);
    try {
      owner.gainMoney(value);
    } catch (RuntimeException failure) {
      state.changeMortgageState(false);
      throw failure;
    }
    return value;
  }

  /**
   * 判斷玩家現在能否贖回地產。
   *
   * @param owner 持有人
   * @param property 目標地產
   */
  public boolean canUnmortgage(Player owner, Property property) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    return property.isOwnedBy(owner) && property.isMortgaged();
  }

  /**
   * 贖回地產：支付抵押金加上一成利息。
   *
   * @param owner 持有人
   * @param property 目標地產
   * @return 實際支付的金額
   * @throws InvalidActionException 不是持有人，或地產不在抵押狀態
   * @throws InsufficientFundsException 現金不足（此時地產維持抵押）
   * @throws BankruptcyException 玩家實作判定為破產時
   */
  public int unmortgage(Player owner, Property property)
      throws InvalidActionException, InsufficientFundsException, BankruptcyException {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    if (!property.isOwnedBy(owner)) {
      throw new InvalidActionException("只有持有人可以贖回 " + property.getName() + "。");
    }
    if (!property.isMortgaged()) {
      throw new InvalidActionException(property.getName() + " 並未抵押。");
    }

    int cost = property.getUnmortgageCost();
    PropertyState state = stateOf(property);
    // 先付款，成功後才解除抵押；解除失敗時把錢退回去，地產維持抵押。
    owner.decMoney(cost);
    try {
      state.changeMortgageState(false);
    } catch (RuntimeException failure) {
      owner.gainMoney(cost);
      throw failure;
    }
    return cost;
  }

  /**
   * 判斷玩家現在能否把地產賣回銀行。
   *
   * <p><b>這是選用規則，不是官方規則。</b>官方只有「抵押換一半現金」與「拆屋退一半建造費」，
   * 破產時地產由銀行拍賣。這裡提供直接賣回銀行，是為了讓破產清算時能一次把資產變現；
   * 若要嚴格照官方規則，請改用 {@link #mortgage(Player, Property)}。
   *
   * @param owner 持有人
   * @param property 目標地產
   */
  public boolean canSellToBank(Player owner, Property property) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    return property.isOwnedBy(owner) && !hasBuildings(property);
  }

  /**
   * 把地產賣回銀行，取回購買價的一半（選用規則，見 {@link #canSellToBank}）。
   *
   * <p>賣出後地產回到無人持有的狀態；若原本在抵押中，抵押狀態會一併清除，
   * 因為抵押關係是「持有人欠銀行」，持有人消失後就不再存在。
   *
   * @param owner 持有人
   * @param property 目標地產
   * @return 實際取回的金額（購買價的一半，無條件捨去）
   * @throws InvalidActionException 不是持有人，或地產上還有建築
   */
  public int sellToBank(Player owner, Property property) throws InvalidActionException {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(property, "property");
    if (!property.isOwnedBy(owner)) {
      throw new InvalidActionException("只有持有人可以把 " + property.getName() + " 賣回銀行。");
    }
    if (hasBuildings(property)) {
      throw new InvalidActionException(property.getName() + " 上還有建築，必須先拆除。");
    }

    int refund = property.getPurchasePrice() / 2;
    PropertyState state = stateOf(property);
    boolean wasMortgaged = property.isMortgaged();
    // 先移除資產與持有人，再入帳；入帳失敗就全部還原。
    owner.removeProperty(property);
    state.changeOwner(null);
    state.changeMortgageState(false);
    try {
      owner.gainMoney(refund);
    } catch (RuntimeException failure) {
      state.changeOwner(owner);
      state.changeMortgageState(wasMortgaged);
      owner.addProperty(property);
      throw failure;
    }
    return refund;
  }

  /** 取得交易服務可用的地產狀態操作能力。 */
  private static PropertyState stateOf(Property property) {
    if (!(property instanceof PropertyState state)) {
      throw new IllegalArgumentException("地產未提供交易狀態操作能力。");
    }
    return state;
  }

  /**
   * 判斷這筆地產是否還有房屋或酒店。
   *
   * @param property 要檢查的地產
   */
  private static boolean hasBuildings(Property property) {
    return property instanceof Developable developable
        && developable.getBuildingLevel() != BuildingLevel.NONE;
  }
}
