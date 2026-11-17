package wintervillage;

/**
 * Scene-specific JavaFX constants for the two Winter Village generations.
 *
 * <p>The v2 square-lake export keeps the lake centre at JavaFX {@code (0, 0)}
 * and lifts the base underside to {@code Y = 0}; the v1 approved export was
 * recentred by its content bounding box, so it needs its own board/camera
 * offsets. Select a profile with {@code -Dwinter.village.profile=v1|v2}
 * (default {@code v2}).</p>
 */
public final class WinterVillageProfile {

    public final String id;
    public final String label;
    public final String assetFileName;

    public final double boardWidth;
    public final double boardX;
    public final double boardY;
    public final double boardZ;
    public final double pine05ShiftX;

    public final double cameraTargetX;
    public final double cameraTargetY;
    public final double cameraTargetZ;
    public final double cameraAzimuthDeg;
    public final double cameraElevationDeg;
    public final double cameraDistance;

    public final String hudEyebrow;
    public final String hudTitle;
    public final String hudSubtitle;
    public final String cameraInfo;

    private WinterVillageProfile(
            String id,
            String label,
            String assetFileName,
            double boardWidth,
            double boardX,
            double boardY,
            double boardZ,
            double pine05ShiftX,
            double cameraTargetX,
            double cameraTargetY,
            double cameraTargetZ,
            double cameraAzimuthDeg,
            double cameraElevationDeg,
            double cameraDistance,
            String hudEyebrow,
            String hudTitle,
            String hudSubtitle,
            String cameraInfo) {
        this.id = id;
        this.label = label;
        this.assetFileName = assetFileName;
        this.boardWidth = boardWidth;
        this.boardX = boardX;
        this.boardY = boardY;
        this.boardZ = boardZ;
        this.pine05ShiftX = pine05ShiftX;
        this.cameraTargetX = cameraTargetX;
        this.cameraTargetY = cameraTargetY;
        this.cameraTargetZ = cameraTargetZ;
        this.cameraAzimuthDeg = cameraAzimuthDeg;
        this.cameraElevationDeg = cameraElevationDeg;
        this.cameraDistance = cameraDistance;
        this.hudEyebrow = hudEyebrow;
        this.hudTitle = hudTitle;
        this.hudSubtitle = hudSubtitle;
        this.cameraInfo = cameraInfo;
    }

    /** Original approved 26 x 20 m scene with the organic pond. */
    public static final WinterVillageProfile V1 = new WinterVillageProfile(
            "v1",
            "v1 Original",
            "winter_village_sample_v1.glb",
            6.4,
            3.05,
            0.81,
            3.40,
            -0.20,
            -0.013,
            1.78,
            -0.772,
            38.0,
            33.0,
            58.753,
            "APPROVED WINTER VILLAGE",
            "冬季村庄样景",
            "Winter Village v1 · Original Pond",
            "初始机位 38° / 33° · 58.75 m · front +Z");

    /** v2 square-lake scene, 32 x 32 m, 6 cottages, lake centred at (0,0). */
    public static final WinterVillageProfile V2 = new WinterVillageProfile(
            "v2",
            "v2 Square Lake",
            "winter_village_sample_v2.glb",
            7.6,
            0.0,
            0.794,
            0.0,
            0.0,
            0.0,
            0.88,
            0.0,
            38.0,
            50.0,
            86.839,
            "WINTER VILLAGE v2 · SQUARE LAKE",
            "冬季村庄 · 方形冰湖",
            "Winter Village v2 · Square Lake · Tokens + Dice + Cards",
            "初始机位 38° / 50° · 86.84 m · front +Z");

    public static WinterVillageProfile active() {
        String requested = System.getProperty("winter.village.profile", "v2");
        return "v1".equalsIgnoreCase(requested) ? V1 : V2;
    }

    @Override
    public String toString() {
        return id;
    }
}
