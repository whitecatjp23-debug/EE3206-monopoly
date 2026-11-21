package monopoly.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 極簡測試工具：不需要 JUnit，直接用純 Java 斷言。
 *
 * <p>每個測試檔呼叫 {@link #suite(String)} 開啟一個測試群組，
 * 再用 {@link #check} / {@link #checkEquals} / {@link #expectThrows} 驗證結果；
 * 失敗的項目會印出訊息並累計，最後由 {@link AllTests} 統一回報。
 */
public final class TestSupport {
  private static int checkCount;
  private static int failureCount;
  private static final List<String> failureMessages = new ArrayList<>();
  private static String currentSuite = "(未命名)";

  private TestSupport() {
  }

  /** 開啟一個測試群組，之後的訊息會標上群組名稱。 */
  public static void suite(String name) {
    currentSuite = name;
    System.out.println("\n=== " + name + " ===");
  }

  /** 檢查條件成立。 */
  public static void check(boolean condition, String message) {
    checkCount++;
    if (!condition) {
      fail(message);
    }
  }

  /** 檢查兩個值相等（物件）。 */
  public static void checkEquals(Object expected, Object actual, String message) {
    checkCount++;
    if (!Objects.equals(expected, actual)) {
      fail(message + "（預期 " + expected + "，實際 " + actual + "）");
    }
  }

  /** 檢查兩個整數相等。 */
  public static void checkEquals(int expected, int actual, String message) {
    checkCount++;
    if (expected != actual) {
      fail(message + "（預期 " + expected + "，實際 " + actual + "）");
    }
  }

  /**
   * 檢查某段程式碼會拋出指定例外。
   *
   * @param type 預期的例外型別
   * @param action 要執行的程式碼
   * @param message 失敗訊息
   * @return 實際捕捉到的例外
   */
  public static <T extends Throwable> T expectThrows(
      Class<T> type, ThrowingRunnable action, String message) {
    checkCount++;
    try {
      action.run();
    } catch (Throwable thrown) {
      if (type.isInstance(thrown)) {
        @SuppressWarnings("unchecked")
        T matched = (T) thrown;
        return matched;
      }
      fail(message + "（預期 " + type.getSimpleName() + "，實際 "
          + thrown.getClass().getSimpleName() + "）");
      return null;
    }
    fail(message + "（沒有拋出任何例外）");
    return null;
  }

  /**
   * 建立一個具名的測試情境，方便在輸出中定位。
   *
   * <p>允許情境內的程式碼拋出受檢例外（例如 {@code InvalidActionException}），
   * 未預期的例外會被記錄為失敗，而不會中斷整份測試。
   *
   * @param name 情境名稱
   * @param body 情境內容
   */
  public static void scenario(String name, ThrowingRunnable body) {
    System.out.println("-- " + name);
    try {
      body.run();
    } catch (Throwable thrown) {
      checkCount++;
      fail("情境 " + name + " 拋出了未預期的例外：" + thrown);
      thrown.printStackTrace(System.out);
    }
  }

  /** 回傳已執行的檢查數。 */
  public static int getCheckCount() {
    return checkCount;
  }

  /** 回傳失敗的檢查數。 */
  public static int getFailureCount() {
    return failureCount;
  }

  /** 印出所有失敗訊息。 */
  public static void printFailures() {
    for (String message : failureMessages) {
      System.out.println("  ✗ " + message);
    }
  }

  private static void fail(String message) {
    failureCount++;
    String full = "[" + currentSuite + "] " + message;
    failureMessages.add(full);
    System.out.println("  ✗ " + full);
  }

  /** 允許拋出受檢例外的程式碼區塊。 */
  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Exception;
  }
}
