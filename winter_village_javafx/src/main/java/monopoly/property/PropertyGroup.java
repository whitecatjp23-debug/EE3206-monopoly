package monopoly.property;

import monopoly.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 地產組別：同一組的地產會互相影響租金。
 *
 * <ul>
 *   <li>街道：集滿整組時，未蓋房屋的地租加倍。</li>
 *   <li>鐵路：租金隨持有人擁有的鐵路數增加（25／50／100／200）。</li>
 *   <li>公用事業：持有人擁有兩項時，租金由 4 倍骰數改為 10 倍。</li>
 * </ul>
 *
 * <p>地產在建立時會自動向組別登記（見 {@link AbstractProperty} 建構子），
 * 因此租金計算不需要呼叫端自行傳入「同組擁有數」，避免傳錯數字。
 */
public final class PropertyGroup {
  private final String name;
  private final int requiredProperties;
  private final int buildingCost;
  /** 已登記到此組別的地產，依建立順序排列。 */
  private final List<Property> members = new ArrayList<>();

  /**
   * 建立組別。
   *
   * @param name 組別名稱，例如 Brown、Railroad、Utility
   * @param requiredProperties 集滿整組所需的數量
   * @param buildingCost 每棟房屋／酒店的建造費用；鐵路與公用事業傳 0
   */
  public PropertyGroup(String name, int requiredProperties, int buildingCost) {
    this.name = Objects.requireNonNull(name, "name");
    if (requiredProperties <= 0) {
      throw new IllegalArgumentException("組別所需數量必須大於 0。");
    }
    if (buildingCost < 0) {
      throw new IllegalArgumentException("建造費用不可為負數。");
    }
    this.requiredProperties = requiredProperties;
    this.buildingCost = buildingCost;
  }

  /** 回傳組別名稱。 */
  public String getName() {
    return name;
  }

  /** 回傳集滿整組所需的數量。 */
  public int getRequiredProperties() {
    return requiredProperties;
  }

  /** 回傳每棟房屋／酒店的建造費用；0 表示此組別不能建造。 */
  public int getBuildingCost() {
    return buildingCost;
  }

  /** 此組別是否可蓋房屋與酒店（只有街道可建造）。 */
  public boolean isBuildable() {
    return buildingCost > 0;
  }

  /**
   * 登記一筆地產；重複登記會被忽略。
   *
   * @param property 要登記的地產
   */
  public void register(Property property) {
    Objects.requireNonNull(property, "property");
    if (property.getGroup() != this) {
      throw new IllegalArgumentException("地產必須登記到自己的組別。");
    }
    if (!members.contains(property)) {
      if (members.size() >= requiredProperties) {
        throw new IllegalArgumentException("組別已達地產數量上限。");
      }
      members.add(property);
    }
  }

  /** 回傳此組別的所有地產（唯讀快照）。 */
  public List<Property> getMembers() {
    return List.copyOf(members);
  }

  /**
   * 回傳指定玩家在此組別擁有的地產數量。
   *
   * @param player 要查詢的玩家；null 時回傳 0
   */
  public int countOwnedBy(Player player) {
    if (player == null) {
      return 0;
    }
    int count = 0;
    for (Property member : members) {
      if (player.equals(member.getOwner())) {
        count++;
      }
    }
    return count;
  }

  /**
   * 回傳指定玩家在此組別擁有的地產清單。
   *
   * @param player 要查詢的玩家；null 時回傳空清單
   */
  public List<Property> getMembersOwnedBy(Player player) {
    List<Property> owned = new ArrayList<>();
    if (player == null) {
      return owned;
    }
    for (Property member : members) {
      if (player.equals(member.getOwner())) {
        owned.add(member);
      }
    }
    return owned;
  }

  /**
   * 依擁有數量判斷是否集滿整組。
   *
   * @param ownedCount 玩家在此組別擁有的數量
   */
  public boolean isComplete(int ownedCount) {
    return ownedCount >= requiredProperties;
  }

  /**
   * 判斷指定玩家是否集滿整組。
   *
   * @param player 要查詢的玩家
   */
  public boolean isCompleteFor(Player player) {
    return isComplete(countOwnedBy(player));
  }

  /**
   * 判斷此組別是否有任何一筆被抵押。
   *
   * <p>官方規則是「組別內有地產被抵押時，整組都不可建造」，建造規則會用到。
   */
  public boolean hasMortgagedMember() {
    for (Property member : members) {
      if (member.isMortgaged()) {
        return true;
      }
    }
    return false;
  }

  /**
   * 回傳組內可建造地產的最低建築等級（沒有可建造地產時為 0）。
   *
   * <p>「平均建造」的判準就是這個數字：只有等級等於最低值的地產才能再加一棟，
   * 因此同組差距永遠不會超過 1 級。鐵路與公用事業不是可建造地產，會被略過。
   */
  public int minBuildingLevel() {
    int min = Integer.MAX_VALUE;
    for (Property member : members) {
      if (member instanceof Developable developable) {
        min = Math.min(min, developable.getBuildingLevel().getLevel());
      }
    }
    return min == Integer.MAX_VALUE ? 0 : min;
  }

  /**
   * 回傳組內可建造地產的最高建築等級（沒有可建造地產時為 0）。
   *
   * <p>「平均拆除」的判準就是這個數字：只有等級等於最高值的地產才能拆一級。
   */
  public int maxBuildingLevel() {
    int max = 0;
    for (Property member : members) {
      if (member instanceof Developable developable) {
        max = Math.max(max, developable.getBuildingLevel().getLevel());
      }
    }
    return max;
  }

  /**
   * 判斷指定地產是否為此組別目前「合法」的建造目標。
   *
   * <p>只檢查平均建造這一條，其餘規則（集滿整組、無抵押、費用）由
   * {@link PropertyDevelopment} 負責；放在這裡是因為它需要窺看整組的建築狀態，
   * 屬於組別自己的知識。
   *
   * @param property 要檢查的地產；不是本組成員時回傳 false
   */
  public boolean isLegalBuildTarget(Property property) {
    return property instanceof Developable developable
        && members.contains(property)
        && developable.getBuildingLevel().getLevel() == minBuildingLevel();
  }

  /**
   * 判斷指定地產是否為此組別目前「合法」的拆除目標（平均拆除）。
   *
   * @param property 要檢查的地產；不是本組成員時回傳 false
   */
  public boolean isLegalSellTarget(Property property) {
    return property instanceof Developable developable
        && members.contains(property)
        && developable.getBuildingLevel().getLevel() == maxBuildingLevel();
  }

  @Override
  public String toString() {
    return name + "(" + members.size() + "/" + requiredProperties + ")";
  }
}
