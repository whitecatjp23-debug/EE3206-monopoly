package monopoly.test;

/**
 * 測試入口：依序執行經濟、棋盤與地產測試，最後統一回報結果。
 *
 * <p>用法（在 {@code winter_village_javafx} 目錄下）：
 * <pre>{@code ./test.sh}</pre>
 * 全部通過時結束碼為 0，有失敗時為 1，方便日後接 CI。
 */
public final class AllTests {
  private AllTests() {
  }

  /** 執行全部測試。 */
  public static void main(String[] args) {
    System.out.println("=== 大富翁：經濟／棋盤／地產 測試 ===");
    EconomyTests.run();
    BoardTests.run();
    PropertyTests.run();

    System.out.println();
    if (TestSupport.getFailureCount() == 0) {
      System.out.println("全部 " + TestSupport.getCheckCount() + " 項檢查通過。");
      return;
    }
    TestSupport.printFailures();
    System.out.println("共 " + TestSupport.getFailureCount() + " 項失敗（總檢查數 "
        + TestSupport.getCheckCount() + "）。");
    System.exit(1);
  }
}
