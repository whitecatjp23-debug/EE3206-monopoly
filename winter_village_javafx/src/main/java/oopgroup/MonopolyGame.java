package oopgroup;

import javafx.collections.ObservableList;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 遊戲核心邏輯 — MonopolyGame
 * 負責：回合管理、勝負判定、破產清算、存檔/讀檔
 */
public final class MonopolyGame {

    public static final String SAVE_MAGIC = "WINTER_VILLAGE_MONOPOLY_V1";

    private MonopolyGame() { /* 純靜態工具 */ }

    // ─────────────────────────────────────────────────────────────
    // 回合邏輯
    // ─────────────────────────────────────────────────────────────

    /** 回傳下一位尚未破產玩家的索引（環形）；若全部破產則回傳原索引。 */
    public static int nextActivePlayerIndex(List<WinterVillagePlayerHUD.Player> players, int currentIndex) {
        if (players == null || players.isEmpty()) return currentIndex;
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int candidate = (currentIndex + i) % n;
            if (!players.get(candidate).isBankrupt()) return candidate;
        }
        return currentIndex;
    }

    public static int bumpTurn(int currentTurn) { return currentTurn + 1; }

    // ─────────────────────────────────────────────────────────────
    // 勝負判定
    // ─────────────────────────────────────────────────────────────

    public static List<WinterVillagePlayerHUD.Player> activePlayers(List<WinterVillagePlayerHUD.Player> players) {
        List<WinterVillagePlayerHUD.Player> out = new ArrayList<>();
        if (players == null) return out;
        for (WinterVillagePlayerHUD.Player p : players) if (!p.isBankrupt()) out.add(p);
        return out;
    }

    public static boolean hasWinner(List<WinterVillagePlayerHUD.Player> players) {
        return activePlayers(players).size() <= 1;
    }

    public static WinterVillagePlayerHUD.Player getWinner(List<WinterVillagePlayerHUD.Player> players) {
        List<WinterVillagePlayerHUD.Player> alive = activePlayers(players);
        return alive.isEmpty() ? null : alive.get(0);
    }

    public static WinterVillagePlayerHUD.Player getHighestNetWorthWinner(List<WinterVillagePlayerHUD.Player> players) {
        WinterVillagePlayerHUD.Player best = null;
        for (WinterVillagePlayerHUD.Player p : players) {
            if (p.isBankrupt()) continue;
            if (best == null || p.calculateNetWorth() > best.calculateNetWorth()) best = p;
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────
    // 破產清算
    // ─────────────────────────────────────────────────────────────

    public static void liquidateAndMarkBankrupt(WinterVillagePlayerHUD.Player p) {
        if (p == null) return;
        List<WinterVillagePlayerHUD.PropertyData> owned = new ArrayList<>(p.getOwnedProperties());
        for (WinterVillagePlayerHUD.PropertyData prop : owned) {
            while (prop.getBuildingCount() > 0) {
                try { prop.removeBuilding(); } catch (Exception ignored) { break; }
            }
            prop.setMortgaged(false);
            p.removeProperty(prop);
        }
        p.setBalance(0);
        p.setBankrupt(true);
    }

    // ─────────────────────────────────────────────────────────────
    // 存檔 / 讀檔
    // ─────────────────────────────────────────────────────────────

    public static final class SaveState {
        public int currentPlayerIndex;
        public int lastDiceSum;
        public int turnNumber;
    }

    public static void save(Path file,
                            ObservableList<WinterVillagePlayerHUD.Player> players,
                            ObservableList<WinterVillagePlayerHUD.PropertyData> board,
                            int currentPlayerIndex,
                            int lastDiceSum,
                            int turnNumber) throws IOException {
        if (file == null) throw new IOException("Save path is null");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println(SAVE_MAGIC);
            w.println("currentPlayerIndex=" + currentPlayerIndex);
            w.println("lastDiceSum=" + lastDiceSum);
            w.println("turnNumber=" + turnNumber);
            w.println("playerCount=" + players.size());
            for (WinterVillagePlayerHUD.Player p : players) {
                w.println("PLAYER|" + safe(p.getId()) + "|" + safe(p.getName()) + "|"
                        + safe(p.getTokenEmoji()) + "|" + safe(p.getHexColor()) + "|"
                        + p.getBalance() + "|" + p.getPosition() + "|"
                        + p.isInJail() + "|" + p.getJailTurns() + "|"
                        + p.getGetOutOfJailCards() + "|" + p.isBankrupt());
            }
            w.println("boardCount=" + board.size());
            for (WinterVillagePlayerHUD.PropertyData pd : board) {
                String ownerId = pd.getOwner() == null ? "NONE" : pd.getOwner().getId();
                w.println("PROP|" + pd.getTileIndex() + "|" + safe(ownerId) + "|"
                        + pd.getBuildingCount() + "|" + pd.isMortgaged());
            }
        }
    }

    public static SaveState load(Path file,
                                 ObservableList<WinterVillagePlayerHUD.Player> players,
                                 ObservableList<WinterVillagePlayerHUD.PropertyData> board) throws IOException {
        if (file == null) throw new IOException("Load path is null");
        SaveState state = new SaveState();
        state.currentPlayerIndex = 0;
        state.lastDiceSum = 7;
        state.turnNumber = 1;

        List<String[]> playerLines = new ArrayList<>();
        List<String[]> propLines = new ArrayList<>();

        try (BufferedReader r = Files.newBufferedReader(file)) {
            String magic = r.readLine();
            if (!SAVE_MAGIC.equals(magic)) throw new IOException("Not a Winter Village Monopoly save file");
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("currentPlayerIndex=")) state.currentPlayerIndex = parseEq(line);
                else if (line.startsWith("lastDiceSum="))    state.lastDiceSum = parseEq(line);
                else if (line.startsWith("turnNumber="))     state.turnNumber = parseEq(line);
                else if (line.startsWith("PLAYER|"))         playerLines.add(line.split("\\|", -1));
                else if (line.startsWith("PROP|"))           propLines.add(line.split("\\|", -1));
            }
        }

        players.clear();
        Map<String, WinterVillagePlayerHUD.Player> byId = new HashMap<>();
        for (String[] parts : playerLines) {
            if (parts.length < 11) continue;
            WinterVillagePlayerHUD.Player p = new WinterVillagePlayerHUD.Player(
                    parts[1], parts[2], parts[3], parts[4], Integer.parseInt(parts[5]));
            p.setPosition(Integer.parseInt(parts[6]));
            p.restoreJailState(Boolean.parseBoolean(parts[7]), Integer.parseInt(parts[8]));
            int cards = Integer.parseInt(parts[9]);
            for (int i = 0; i < cards; i++) p.addGetOutOfJailCard();
            if (Boolean.parseBoolean(parts[10])) p.setBankrupt(true);
            players.add(p);
            byId.put(p.getId(), p);
        }

        Map<Integer, WinterVillagePlayerHUD.PropertyData> byTile = new HashMap<>();
        for (WinterVillagePlayerHUD.PropertyData pd : board) {
            byTile.put(pd.getTileIndex(), pd);
            while (pd.getBuildingCount() > 0) {
                try { pd.removeBuilding(); } catch (Exception ignored) { break; }
            }
            pd.setMortgaged(false);
            if (pd.getOwner() != null) pd.getOwner().removeProperty(pd);
        }

        for (String[] parts : propLines) {
            if (parts.length < 5) continue;
            WinterVillagePlayerHUD.PropertyData pd = byTile.get(Integer.parseInt(parts[1]));
            if (pd == null) continue;
            if (!"NONE".equals(parts[2])) {
                WinterVillagePlayerHUD.Player owner = byId.get(parts[2]);
                if (owner != null) owner.addProperty(pd);
            }
            int buildings = Integer.parseInt(parts[3]);
            for (int i = 0; i < buildings; i++) {
                try { pd.addBuilding(); } catch (Exception ignored) { break; }
            }
            pd.setMortgaged(Boolean.parseBoolean(parts[4]));
        }
        return state;
    }

    private static int parseEq(String line) { return Integer.parseInt(line.substring(line.indexOf('=') + 1)); }
    private static String safe(String s) { return s == null ? "" : s.replace("|", "/"); }
}