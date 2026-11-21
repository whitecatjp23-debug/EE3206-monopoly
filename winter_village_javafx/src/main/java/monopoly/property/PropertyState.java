package monopoly.property;

import monopoly.Player;

/** 交易服務使用的地產內部狀態操作能力，不暴露給一般呼叫端。 */
interface PropertyState {
  /** 設定持有人。 */
  void changeOwner(Player owner);

  /** 設定抵押狀態。 */
  void changeMortgageState(boolean mortgaged);
}
