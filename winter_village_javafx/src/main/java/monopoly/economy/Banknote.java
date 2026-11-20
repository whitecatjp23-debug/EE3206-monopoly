package monopoly.economy;

import java.util.Objects;

/**
 * 一張實體紙鈔，其價值完全由面額決定，建立後不可變。
 *
 * <p>{@link Bank} 發鈔時建立紙鈔，玩家付款時再把紙鈔交回銀行；紙鈔不記錄流水號，
 * 因為遊戲只需要面額與張數，不需要追蹤個別鈔票的流向。
 *
 * @param denomination 紙鈔面額
 */
public record Banknote(Denomination denomination) {
  /** 建立紙鈔，面額不可為 null。 */
  public Banknote {
    Objects.requireNonNull(denomination, "denomination");
  }

  /** 回傳紙鈔面額。 */
  public Denomination getDenomination() {
    return denomination;
  }

  /** 回傳紙鈔幣值（元），等同 {@code denomination().getValue()}。 */
  public int getValue() {
    return denomination.getValue();
  }
}
