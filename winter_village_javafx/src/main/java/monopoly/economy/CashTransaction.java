package monopoly.economy;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * 現金交易服務：把「錢包」與「銀行」串成遊戲中實際會發生的付款流程。
 *
 * <p>官方規則的付款方式是「拿出足夠的鈔票，銀行負責找零」，
 * 所以這裡的每一個付款方法都是：付款方拿出總值不小於金額的鈔票 → 銀行收下 →
 * 銀行找出差額（{@link Bank#withdraw(int)}，存貨不足時加印）→ 收款方拿到紙鈔。
 *
 * <p>若付款方現金根本不足，方法會拋出 {@link InsufficientCashException} 且不改動任何餘額，
 * 讓規則層可以接著走抵押或破產流程。
 */
public final class CashTransaction {
  private final Bank bank;

  /**
   * 建立交易服務。
   *
   * @param bank 對局中唯一的銀行
   */
  public CashTransaction(Bank bank) {
    this.bank = Objects.requireNonNull(bank, "bank");
  }

  /** 回傳本服務使用的銀行。 */
  public Bank getBank() {
    return bank;
  }

  /**
   * 玩家把現金交給銀行（繳稅、付給銀行的費用），銀行負責找零。
   *
   * @param payer 付款方
   * @param amount 金額
   * @return 玩家實際交出的紙鈔（總值可能大於 {@code amount}）
   * @throws InsufficientCashException 現金不足
   */
  public List<Banknote> payToBank(Wallet payer, int amount) throws InsufficientCashException {
    Objects.requireNonNull(payer, "payer");
    if (amount < 0) {
      throw new IllegalArgumentException("付款金額不可為負數。");
    }
    if (amount == 0) {
      return List.of();
    }
    // 先確認現金足夠，避免拿出鈔票後才失敗。
    requireBalance(payer, amount);
    List<Banknote> handed = payer.takeCovering(amount);
    bank.deposit(handed);

    int change = valueOf(handed) - amount;
    List<Banknote> changeNotes = bank.withdraw(change);
    payer.addAll(changeNotes);
    return handed;
  }

  /**
   * 銀行發錢給玩家（經過 GO、抽到獎金卡）。
   *
   * @param receiver 收款方
   * @param amount 金額
   * @return 收到的紙鈔
   */
  public List<Banknote> payFromBank(Wallet receiver, int amount) {
    Objects.requireNonNull(receiver, "receiver");
    List<Banknote> issued = bank.withdraw(amount);
    receiver.addAll(issued);
    return issued;
  }

  /**
   * 玩家之間的付款（付租金給地主），銀行負責找零。
   *
   * @param payer 付款方
   * @param payee 收款方
   * @param amount 金額
   * @return 收款方實際收到的紙鈔
   * @throws InsufficientCashException 付款方現金不足
   */
  public List<Banknote> payPlayer(Wallet payer, Wallet payee, int amount)
      throws InsufficientCashException {
    Objects.requireNonNull(payer, "payer");
    Objects.requireNonNull(payee, "payee");
    if (amount < 0) {
      throw new IllegalArgumentException("付款金額不可為負數。");
    }
    if (amount == 0) {
      return List.of();
    }
    requireBalance(payer, amount);

    List<Banknote> handed = payer.takeCovering(amount);
    bank.deposit(handed);

    // 找零：銀行先把差額還給付款方，再把剛好金額的鈔票交給收款方。
    int change = valueOf(handed) - amount;
    payer.addAll(bank.withdraw(change));
    List<Banknote> received = bank.withdraw(amount);
    payee.addAll(received);
    return received;
  }

  /**
   * 判斷付款方目前現金是否足夠支付。
   *
   * @param payer 付款方
   * @param amount 金額
   * @return 是否足夠
   */
  public boolean canPay(Wallet payer, int amount) {
    Objects.requireNonNull(payer, "payer");
    if (amount < 0) {
      throw new IllegalArgumentException("金額不可為負數。");
    }
    return payer.getBalance() >= amount;
  }

  /**
   * 找出付款方在銀行配合找零後能付得出的最大金額（用於破產清算提示）。
   *
   * @param payer 付款方
   * @return 可支付的最大金額，等於錢包總值
   */
  public int getPayableAmount(Wallet payer) {
    return Objects.requireNonNull(payer, "payer").getBalance();
  }

  /**
   * 換鈔：把錢包整疊存回銀行，再領回同樣總額、張數最少的組合。
   *
   * <p>換鈔不改變錢包與銀行的總值；存貨不足時由銀行加印，因此一定成功。
   *
   * @param wallet 要換鈔的錢包
   */
  public void rebalance(Wallet wallet) {
    Objects.requireNonNull(wallet, "wallet");
    int balance = wallet.getBalance();
    if (balance == 0) {
      return;
    }

    // 新組合可能需要用到銀行現有的鈔票，因此先把舊鈔併入銀行存貨再計算可行性。
    EnumMap<Denomination, Integer> available =
        ChangeCalculator.merge(bank.getInventory(), ChangeCalculator.count(wallet.getNotes()));
    if (ChangeCalculator.fewestNotes(available, balance).isEmpty()) {
      // 面額含 1 元時不會發生，保留檢查以免日後改動面額組合。
      throw new IllegalStateException("銀行無法湊出 " + balance + " 元。");
    }

    // 舊鈔先回到銀行，再依張數最少的組合領出（存貨不足時銀行加印）。
    bank.deposit(wallet.takeAll());
    wallet.addAll(bank.withdraw(balance));
  }

  /** 確認現金足夠，不足時拋出例外。 */
  private static void requireBalance(Wallet payer, int amount) throws InsufficientCashException {
    if (payer.getBalance() < amount) {
      throw new InsufficientCashException(amount, payer.getBalance());
    }
  }

  /** 計算一疊鈔票的總值。 */
  private static int valueOf(List<Banknote> notes) {
    int total = 0;
    for (Banknote note : notes) {
      total += note.getValue();
    }
    return total;
  }
}
