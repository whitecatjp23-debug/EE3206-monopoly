package monopoly.property;

import monopoly.Player;

import java.util.Objects;

/**
 * 街道、鐵路與公用事業共用的地產狀態。
 *
 * <p>這個類別只保管資料（名稱、價格、組別、持有人、是否抵押），
 * 所有規則檢查與付款都交給 {@link PropertyMarket} 與 {@link PropertyDevelopment}；
 * 因此直接呼叫 setter 不會檢查擁有權，正常流程請走那兩個服務。
 */
public abstract class AbstractProperty implements Property, PropertyState {
  /** 抵押成數：可向銀行取得的金額為購買價的一半。 */
  private static final int MORTGAGE_DIVISOR = 2;
  /** 贖回時銀行收取的利息（10%）。 */
  private static final int UNMORTGAGE_INTEREST_PERCENT = 10;

  private final String name;
  private final int purchasePrice;
  private final PropertyGroup group;
  private Player owner;
  private boolean mortgaged;

  /**
   * 建立地產並自動向組別登記。
   *
   * <p>組別靠這份名單計算租金加成（街道集滿加倍、鐵路依數量、公用事業 4/10 倍），
   * 所以呼叫端不需要自己維護「同組擁有數」。
   *
   * @param name 地產名稱
   * @param purchasePrice 購買價格，不可為負數
   * @param group 所屬組別
   */
  protected AbstractProperty(String name, int purchasePrice, PropertyGroup group) {
    this.name = Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("地產名稱不可為空白。");
    }
    if (purchasePrice < 0) {
      throw new IllegalArgumentException("購買價格不可為負數。");
    }
    this.purchasePrice = purchasePrice;
    this.group = Objects.requireNonNull(group, "group");
    // 建立即登記，避免之後忘記登記導致租金算錯。
    group.register(this);
  }

  /** 回傳地產名稱。 */
  @Override
  public final String getName() {
    return name;
  }

  /** 回傳購買價格。 */
  @Override
  public final int getPurchasePrice() {
    return purchasePrice;
  }

  /** 回傳所屬組別。 */
  @Override
  public final PropertyGroup getGroup() {
    return group;
  }

  /** 回傳目前持有人；尚未售出時為 null。 */
  @Override
  public final Player getOwner() {
    return owner;
  }

  /** 回傳是否處於抵押狀態。 */
  @Override
  public final boolean isMortgaged() {
    return mortgaged;
  }

  /** 回傳抵押可向銀行取得的金額（購買價的一半，無條件捨去）。 */
  @Override
  public final int getMortgageValue() {
    return purchasePrice / MORTGAGE_DIVISOR;
  }

  /** 回傳贖回抵押所需金額（抵押金加上 10% 利息，利息無條件進位）。 */
  @Override
  public final int getUnmortgageCost() {
    int mortgage = getMortgageValue();
    int interest = (mortgage * UNMORTGAGE_INTEREST_PERCENT + 99) / 100;
    return mortgage + interest;
  }

  @Override
  public final void changeOwner(Player owner) {
    this.owner = owner;
  }

  /** 設定抵押狀態；只供交易服務使用。 */
  @Override
  public final void changeMortgageState(boolean mortgaged) {
    this.mortgaged = mortgaged;
  }

  @Override
  public String toString() {
    return Property.describe(this) + (mortgaged ? " [抵押中]" : "");
  }
}
