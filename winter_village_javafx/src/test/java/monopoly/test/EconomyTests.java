package monopoly.test;

import monopoly.economy.Bank;
import monopoly.economy.Banknote;
import monopoly.economy.CashTransaction;
import monopoly.economy.Denomination;
import monopoly.economy.InsufficientCashException;
import monopoly.economy.Wallet;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static monopoly.test.TestSupport.check;
import static monopoly.test.TestSupport.checkEquals;
import static monopoly.test.TestSupport.expectThrows;
import static monopoly.test.TestSupport.scenario;

/**
 * 經濟系統測試：面額、紙鈔、錢包、銀行與現金交易。
 *
 * <p>重點驗證「官方找零規則」：付款方拿出總值不小於金額的鈔票，銀行負責找零；
 * 銀行存貨不足時可以加印，但加印的張數必須被記錄下來。
 */
public final class EconomyTests {
  private EconomyTests() {
  }

  /** 執行全部經濟系統測試。 */
  public static void run() {
    DenominationTests.run();
    WalletTests.run();
    BankTests.run();
    CashTransactionTests.run();
  }

  /** 面額與紙鈔的基本行為。 */
  static final class DenominationTests {
    static void run() {
      TestSupport.suite("經濟：面額與紙鈔");
      scenario("面額種類與幣值", () -> {
        int[] expected = {1, 5, 10, 20, 50, 100, 500};
        Denomination[] values = Denomination.values();
        checkEquals(expected.length, values.length, "面額數量應為 7 種");
        for (int i = 0; i < expected.length; i++) {
          checkEquals(expected[i], values[i].getValue(), "第 " + i + " 種面額的幣值");
        }
      });

      scenario("依幣值查面額", () -> {
        checkEquals(Denomination.TWENTY, Denomination.fromValue(20), "20 元應對應 TWENTY");
        checkEquals(Denomination.FIVE_HUNDRED, Denomination.fromValue(500),
            "500 元應對應 FIVE_HUNDRED");
        expectThrows(IllegalArgumentException.class,
            () -> Denomination.fromValue(30), "30 元不是合法面額");
      });

      scenario("面額由大到小排序", () -> {
        List<Denomination> order = Denomination.largestFirst();
        checkEquals(Denomination.FIVE_HUNDRED, order.get(0), "第一個應為 500 元");
        checkEquals(Denomination.ONE, order.get(order.size() - 1), "最後一個應為 1 元");
        int previous = Integer.MAX_VALUE;
        for (Denomination denomination : order) {
          check(denomination.getValue() <= previous, "排序應嚴格遞減");
          previous = denomination.getValue();
        }
      });

      scenario("紙鈔值與相等性", () -> {
        Banknote note = new Banknote(Denomination.FIFTY);
        checkEquals(50, note.getValue(), "50 元紙鈔的幣值");
        checkEquals(Denomination.FIFTY, note.getDenomination(), "紙鈔面額");
        checkEquals(new Banknote(Denomination.FIFTY), note, "同面額紙鈔應相等");
        expectThrows(NullPointerException.class,
            () -> new Banknote(null), "紙鈔面額不可為 null");
      });
    }
  }

  /** 錢包：保管鈔票並拿出足夠金額。 */
  static final class WalletTests {
    static void run() {
      TestSupport.suite("經濟：錢包");
      scenario("空錢包", () -> {
        Wallet wallet = new Wallet();
        check(wallet.isEmpty(), "新錢包應為空");
        checkEquals(0, wallet.getBalance(), "新錢包餘額為 0");
        checkEquals(0, wallet.getNoteCount(), "新錢包張數為 0");
      });

      scenario("放入鈔票並計算餘額", () -> {
        Wallet wallet = new Wallet();
        wallet.add(new Banknote(Denomination.ONE_HUNDRED));
        wallet.add(new Banknote(Denomination.FIFTY));
        wallet.addAll(List.of(
            new Banknote(Denomination.TWENTY),
            new Banknote(Denomination.TEN)));
        checkEquals(180, wallet.getBalance(), "100+50+20+10 = 180");
        checkEquals(4, wallet.getNoteCount(), "共 4 張");
        checkEquals(1, wallet.countOf(Denomination.TWENTY), "20 元應有 1 張（含 addAll）");
        checkEquals(0, wallet.countOf(Denomination.FIVE), "沒有 5 元紙鈔");
      });

      scenario("拿出足以支付的一疊鈔票", () -> {
        Wallet wallet = new Wallet(List.of(
            new Banknote(Denomination.FIFTY),
            new Banknote(Denomination.TWENTY)));
        List<Banknote> hand = wallet.takeCovering(60);
        int total = hand.stream().mapToInt(Banknote::getValue).sum();
        check(total >= 60, "交出的鈔票總值應不小於 60");
        checkEquals(70, total, "50+20 只能湊出 70");
        check(wallet.isEmpty(), "拿光後錢包應為空");
        checkEquals(0, wallet.getBalance(), "拿光後餘額為 0");
      });

      scenario("現金不足時拋例外且不扣款", () -> {
        Wallet wallet = new Wallet(List.of(new Banknote(Denomination.TEN)));
        InsufficientCashException error = expectThrows(
            InsufficientCashException.class,
            () -> wallet.takeCovering(25),
            "只有 10 元卻要付 25 元應失敗");
        checkEquals(25, error.getRequired(), "例外記錄的需求金額");
        checkEquals(10, error.getAvailable(), "例外記錄的可用金額");
        checkEquals(10, wallet.getBalance(), "失敗後餘額不變");
      });

      scenario("付款 0 元不需拿出鈔票", () -> {
        Wallet wallet = new Wallet(List.of(new Banknote(Denomination.TEN)));
        check(wallet.takeCovering(0).isEmpty(), "0 元不需交出鈔票");
        checkEquals(10, wallet.getBalance(), "餘額不變");
      });
    }
  }

  /** 銀行：存貨、提領、加印。 */
  static final class BankTests {
    static void run() {
      TestSupport.suite("經濟：銀行");
      scenario("以存貨提領", () -> {
        Bank bank = Bank.of(100, 2, 50, 2, 20, 5);
        checkEquals(400, bank.getTotalValue(), "存貨總值 100×2+50×2+20×5");
        List<Banknote> notes = bank.withdraw(170);
        checkEquals(170, total(notes), "提領金額應為 170");
        checkEquals(3, notes.size(), "170 應為 100+50+20 三張");
        checkEquals(230, bank.getTotalValue(), "存貨剩 230");
        checkEquals(0, bank.getPrintedNoteCount(), "存貨足夠時不應加印");
      });

      scenario("存貨不足時加印並記錄", () -> {
        Bank bank = Bank.of(50, 1);
        List<Banknote> notes = bank.withdraw(60);
        checkEquals(60, total(notes), "仍應支付 60");
        checkEquals(0, bank.getTotalValue(), "存貨的 50 元被領走");
        checkEquals(1, bank.getPrintedNoteCount(), "需加印 1 張（10 元）");
        checkEquals(10, bank.getPrintedValue(), "加印總值為 10");
      });

      scenario("收下鈔票", () -> {
        Bank bank = Bank.of(10, 1);
        bank.deposit(List.of(
            new Banknote(Denomination.ONE_HUNDRED),
            new Banknote(Denomination.FIVE)));
        checkEquals(115, bank.getTotalValue(), "存貨總值 10+100+5");
        checkEquals(3, bank.getTotalNoteCount(), "共存 3 張");
      });

      scenario("存貨湊不出金額時不加印", () -> {
        Bank bank = Bank.of(50, 1);
        check(!bank.canWithdrawFromInventory(60), "50 元存貨湊不出 60");
        expectThrows(InsufficientCashException.class,
            () -> bank.withdrawFromInventoryOnly(60),
            "嚴格模式湊不出時應失敗");
        checkEquals(50, bank.getTotalValue(), "失敗後存貨不變");
      });

      scenario("建構子拒絕非法存貨", () -> {
        Map<Denomination, Integer> bad = new EnumMap<>(Denomination.class);
        bad.put(Denomination.TEN, -1);
        expectThrows(IllegalArgumentException.class,
            () -> new Bank(bad), "負數張數應被拒絕");
        expectThrows(IllegalArgumentException.class,
            () -> new Bank(null), "null 存貨應被拒絕");
        expectThrows(IllegalArgumentException.class,
            () -> Bank.of(20), "參數未成對應被拒絕");
        expectThrows(IllegalArgumentException.class,
            () -> Bank.of(30, 1), "30 元不是合法面額");
      });
    }
  }

  /** 現金交易：找零與現金守恆。 */
  static final class CashTransactionTests {
    static void run() {
      TestSupport.suite("經濟：現金交易");

      scenario("付款給銀行並由銀行找零", () -> {
        Bank bank = Bank.of(10, 10, 5, 10);
        CashTransaction cash = new CashTransaction(bank);
        Wallet payer = new Wallet(List.of(new Banknote(Denomination.ONE_HUNDRED)));
        List<Banknote> handed = cash.payToBank(payer, 60);
        checkEquals(100, total(handed), "付 60 元時交出 100 元鈔票");
        checkEquals(40, payer.getBalance(), "找零後錢包應為 40");
        checkEquals(4, payer.countOf(Denomination.TEN), "找零應為 4 張 10 元");
      });

      scenario("玩家之間付租金", () -> {
        Bank bank = Bank.of(10, 10);
        CashTransaction cash = new CashTransaction(bank);
        Wallet payer = new Wallet(List.of(new Banknote(Denomination.ONE_HUNDRED)));
        Wallet owner = new Wallet();
        cash.payPlayer(payer, owner, 30);
        checkEquals(70, payer.getBalance(), "付款方剩 70");
        checkEquals(30, owner.getBalance(), "地主收到 30");
        checkEquals(3, owner.countOf(Denomination.TEN), "地主收到 3 張 10 元");
      });

      scenario("銀行發錢", () -> {
        Bank bank = Bank.of(100, 5);
        CashTransaction cash = new CashTransaction(bank);
        Wallet player = new Wallet();
        cash.payFromBank(player, 200);
        checkEquals(200, player.getBalance(), "收到 200");
        checkEquals(2, player.countOf(Denomination.ONE_HUNDRED), "收到 2 張 100 元");
      });

      scenario("現金不足時所有餘額不變", () -> {
        Bank bank = Bank.of(10, 10);
        CashTransaction cash = new CashTransaction(bank);
        Wallet payer = new Wallet(List.of(new Banknote(Denomination.TEN)));
        Wallet owner = new Wallet();
        expectThrows(InsufficientCashException.class,
            () -> cash.payPlayer(payer, owner, 50),
            "只有 10 元付不出 50 元租金");
        checkEquals(10, payer.getBalance(), "付款方餘額不變");
        checkEquals(0, owner.getBalance(), "地主未收到錢");
        checkEquals(100, bank.getTotalValue(), "銀行存貨不變");
        check(!cash.canPay(payer, 50), "canPay 應回傳 false");
      });

      scenario("換鈔後張數不增加且總值不變", () -> {
        // 銀行需備有 500 元紙鈔，換鈔才可能換成張數最少的組合。
        Bank bank = Bank.of(500, 10, 100, 10, 50, 10, 20, 10, 10, 10);
        CashTransaction cash = new CashTransaction(bank);
        // 1000 元用 20 張 50 元持有，換鈔後應變成 2 張 500 元。
        Wallet wallet = new Wallet();
        for (int i = 0; i < 20; i++) {
          wallet.add(new Banknote(Denomination.FIFTY));
        }
        cash.rebalance(wallet);
        checkEquals(1000, wallet.getBalance(), "換鈔後總值不變");
        checkEquals(2, wallet.getNoteCount(), "換鈔後應為 2 張 500 元");
        checkEquals(2, wallet.countOf(Denomination.FIVE_HUNDRED), "應為 500 元紙鈔");
      });

      scenario("200 次付款後現金守恆", () -> {
        Bank bank = thoroughBank();
        CashTransaction cash = new CashTransaction(bank);
        Wallet[] wallets = {walletOf(3000), walletOf(1500), walletOf(800)};
        int invariant = totalCash(wallets, bank);
        int[] amounts = {15, 60, 125, 250, 400};
        for (int round = 0; round < 200; round++) {
          int from = round % wallets.length;
          int to = (round + 1) % wallets.length;
          int amount = amounts[round % amounts.length];
          if (!cash.canPay(wallets[from], amount)) {
            cash.payFromBank(wallets[from], amount);
          }
          cash.payPlayer(wallets[from], wallets[to], amount);
          checkEquals(invariant, totalCash(wallets, bank),
              "第 " + round + " 次付款後現金總值應守恆");
        }
      });
    }

    /** 建立存貨充足的銀行。 */
    private static Bank thoroughBank() {
      return new Bank(new EnumMap<>(Map.of(
          Denomination.FIVE_HUNDRED, 20,
          Denomination.ONE_HUNDRED, 40,
          Denomination.FIFTY, 40,
          Denomination.TWENTY, 40,
          Denomination.TEN, 40,
          Denomination.FIVE, 40,
          Denomination.ONE, 40)));
    }

    /** 建立一份指定總額的錢包（全部用 20 元與 10 元湊）。 */
    private static Wallet walletOf(int amount) {
      Wallet wallet = new Wallet();
      int remaining = amount;
      while (remaining >= 100) {
        wallet.add(new Banknote(Denomination.ONE_HUNDRED));
        remaining -= 100;
      }
      while (remaining >= 20) {
        wallet.add(new Banknote(Denomination.TWENTY));
        remaining -= 20;
      }
      while (remaining >= 5) {
        wallet.add(new Banknote(Denomination.FIVE));
        remaining -= 5;
      }
      while (remaining >= 1) {
        wallet.add(new Banknote(Denomination.ONE));
        remaining -= 1;
      }
      return wallet;
    }

    /** 計算場上現金總值（扣除銀行加印的部分）。 */
    private static int totalCash(Wallet[] wallets, Bank bank) {
      int total = bank.getTotalValue() - bank.getPrintedValue();
      for (Wallet wallet : wallets) {
        total += wallet.getBalance();
      }
      return total;
    }
  }

  /** 計算一疊鈔票的總值。 */
  static int total(List<Banknote> notes) {
    int total = 0;
    for (Banknote note : notes) {
      total += note.getValue();
    }
    return total;
  }
}
