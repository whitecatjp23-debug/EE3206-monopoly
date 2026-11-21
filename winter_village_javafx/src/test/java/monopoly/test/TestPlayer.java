package monopoly.test;

import monopoly.BankruptcyException;
import monopoly.Buyable;
import monopoly.InsufficientFundsException;
import monopoly.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 測試用的玩家實作：只維護現金、位置、監獄狀態與持有的地產。
 *
 * <p>與正式規則層的 {@code Player} 實作無關，純粹讓地產與現金測試可以獨立執行。
 */
public final class TestPlayer implements Player {
  private final String name;
  private int balance;
  private int position;
  private boolean inJail;
  private boolean bankrupt;
  private final List<Buyable> properties = new ArrayList<>();

  /** 故障注入：下一次扣款、入帳或資產清單操作是否要拋例外。 */
  private boolean failOnDecMoney;
  private boolean failOnGainMoney;
  private boolean failOnAddProperty;
  private boolean failOnRemoveProperty;

  /**
   * 建立測試玩家。
   *
   * @param name 玩家名稱
   * @param balance 初始現金
   */
  public TestPlayer(String name, int balance) {
    this.name = name;
    this.balance = balance;
  }

  /**
   * 讓下一次 {@link #decMoney(int)} 失敗，用來驗證交易失敗後的復原。
   *
   * @param fail true 表示下一次扣款要拋例外（只影響一次）
   */
  public void failNextDecMoney(boolean fail) {
    this.failOnDecMoney = fail;
  }

  /**
   * 讓下一次 {@link #gainMoney(int)} 失敗，用來驗證入帳失敗後的復原。
   *
   * @param fail true 表示下一次入帳要拋例外（只影響一次）
   */
  public void failNextGainMoney(boolean fail) {
    this.failOnGainMoney = fail;
  }

  /**
   * 讓下一次 {@link #addProperty(Buyable)} 失敗。
   *
   * @param fail true 表示下一次加入資產清單要拋例外（只影響一次）
   */
  public void failNextAddProperty(boolean fail) {
    this.failOnAddProperty = fail;
  }

  /**
   * 讓下一次 {@link #removeProperty(Buyable)} 失敗。
   *
   * @param fail true 表示下一次移除資產清單要拋例外（只影響一次）
   */
  public void failNextRemoveProperty(boolean fail) {
    this.failOnRemoveProperty = fail;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getBalance() {
    return balance;
  }

  @Override
  public int getPosition() {
    return position;
  }

  @Override
  public boolean isInJail() {
    return inJail;
  }

  @Override
  public boolean isBankrupt() {
    return bankrupt;
  }

  @Override
  public void gainMoney(int amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("金額不可為負數。");
    }
    if (failOnGainMoney) {
      failOnGainMoney = false;
      throw new IllegalStateException("測試注入：入帳失敗。");
    }
    balance += amount;
  }

  @Override
  public void decMoney(int amount) throws InsufficientFundsException, BankruptcyException {
    if (amount < 0) {
      throw new IllegalArgumentException("金額不可為負數。");
    }
    if (failOnDecMoney) {
      failOnDecMoney = false;
      throw new IllegalStateException("測試注入：扣款失敗。");
    }
    if (amount > balance) {
      // 與正式實作一致：現金不足時不扣款，直接拋例外。
      throw new InsufficientFundsException(amount, balance);
    }
    balance -= amount;
  }

  @Override
  public void setPosition(int newPosition) {
    this.position = newPosition;
  }

  @Override
  public void moveBy(int steps) {
    this.position = Math.floorMod(this.position + steps, 40);
  }

  @Override
  public void sendToJail() {
    this.inJail = true;
    this.position = 10;
  }

  @Override
  public void releaseFromJail() {
    this.inJail = false;
  }

  @Override
  public void addProperty(Buyable property) {
    if (failOnAddProperty) {
      failOnAddProperty = false;
      throw new IllegalStateException("測試注入：加入資產失敗。");
    }
    properties.add(property);
  }

  @Override
  public void removeProperty(Buyable property) {
    if (failOnRemoveProperty) {
      failOnRemoveProperty = false;
      throw new IllegalStateException("測試注入：移除資產失敗。");
    }
    properties.remove(property);
  }

  /** 回傳玩家持有地產的唯讀快照。 */
  @Override
  public List<Buyable> getOwnedProperties() {
    return List.copyOf(properties);
  }
  @Override
  public String toString() {
    return name + "($" + balance + ")";
  }
}
