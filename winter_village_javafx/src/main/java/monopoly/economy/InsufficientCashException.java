package monopoly.economy;

import monopoly.InsufficientFundsException;

/**
 * 當玩家的實體現金不足以支付時拋出。
 *
 * <p>繼承既有的 {@link InsufficientFundsException}，讓規則層可以用同一種例外處理
 * 「餘額不足」的情況，不論現金是以整數餘額或紙鈔表示。
 * 例外會同時回報要求的金額與目前身上的現金。
 */
public class InsufficientCashException extends InsufficientFundsException {
  /**
   * 建立例外。
   *
   * @param required 想支付的金額
   * @param available 目前身上的現金總值
   */
  public InsufficientCashException(int required, int available) {
    super(required, available);
  }

  @Override
  public String getMessage() {
    return String.format("現金不足：需要 $%d，目前只有 $%d。", getRequired(), getAvailable());
  }
}
