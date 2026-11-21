package monopoly.board.tile;

import monopoly.property.Property;

/**
 * 「這一格帶有一筆地產」的能力。
 *
 * <p>棋盤查詢（依地產找格子、列出所有地產、取得某格的地產）只需要知道
 * 「這一格有沒有地產」，不需要知道它實際上是哪一個格子類別；
 * 因此 {@code Board} 依賴這個窄介面，而不是 {@code instanceof PropertyTile}。
 *
 * <p>這也讓未來新增「同樣帶有地產、但不是 {@code PropertyTile}」的格子
 * （例如主題化或拍賣用的地產格）時，不必回頭修改 {@code Board}。
 */
public interface PropertyHolder {
  /** 回傳此格代表的地產。 */
  Property getProperty();
}
