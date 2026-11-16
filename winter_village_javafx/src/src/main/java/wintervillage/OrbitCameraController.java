package wintervillage;

import javafx.geometry.Point3D;
import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Affine;

/**
 * Orbit camera for the winter village diorama.
 *
 * <p>Initial values come from the Blender hero camera of the active scene
 * profile. v1 uses the approved 26 x 20 m pond scene (38 degrees azimuth,
 * 33 degrees elevation, 58.753 m fit distance); v2 uses the square-lake scene
 * (38 degrees azimuth, 50 degrees elevation, 86.839 m fit distance). The
 * composite is ground-contact at Y = 0 and the camera target is supplied by
 * {@link WinterVillageProfile}.</p>
 *
 * <p>The model itself is deliberately left unrotated: glTF is Y-up with front
 * +Z, and this camera sits on the +X/+Z side so the front faces the viewer.</p>
 */
public final class OrbitCameraController {

    private static final Point3D WORLD_UP = new Point3D(0, 1, 0);

    private static final double MIN_ELEVATION = 8.0;
    private static final double MAX_ELEVATION = 82.0;
    private static final double MIN_DISTANCE = 2.5;
    private static final double MAX_DISTANCE = 160.0;

    private final PerspectiveCamera camera;
    private final Point3D initialTarget;
    private final double initialAzimuth;
    private final double initialElevation;
    private final double initialDistance;

    private Point3D target;
    private Point3D eye;
    private double azimuth;
    private double elevation;
    private double distance;

    public OrbitCameraController(PerspectiveCamera camera, WinterVillageProfile profile) {
        this.camera = camera;
        this.initialTarget = new Point3D(
                profile.cameraTargetX, profile.cameraTargetY, profile.cameraTargetZ);
        this.initialAzimuth = profile.cameraAzimuthDeg;
        this.initialElevation = profile.cameraElevationDeg;
        this.initialDistance = profile.cameraDistance;
        reset();
    }

    /** Left-drag: rotate around the target. */
    public void orbitBy(double dx, double dy) {
        azimuth += dx * 0.22;
        elevation = clamp(elevation - dy * 0.18, MIN_ELEVATION, MAX_ELEVATION);
        apply();
    }

    /** Scroll wheel: dolly in/out. */
    public void zoomBy(double deltaY) {
        distance = clamp(distance * Math.exp(-deltaY * 0.0015), MIN_DISTANCE, MAX_DISTANCE);
        apply();
    }

    /** Restore the initial framing of the active scene profile. */
    public void reset() {
        target = initialTarget;
        azimuth = initialAzimuth;
        elevation = initialElevation;
        distance = initialDistance;
        apply();
    }

    /** Set a custom target/framing for review captures or a focused view. */
    public void focus(Point3D newTarget, double newAzimuth, double newElevation, double newDistance) {
        target = newTarget;
        azimuth = newAzimuth;
        elevation = clamp(newElevation, MIN_ELEVATION, MAX_ELEVATION);
        distance = clamp(newDistance, MIN_DISTANCE, MAX_DISTANCE);
        apply();
    }

    public Point3D cameraPosition() {
        return eye;
    }

    public Point3D target() {
        return target;
    }

    public double azimuthDegrees() {
        return azimuth;
    }

    public double elevationDegrees() {
        return elevation;
    }

    public double distance() {
        return distance;
    }

    private void apply() {
        double horizontal = distance * Math.cos(Math.toRadians(elevation));
        eye = target.add(
                horizontal * Math.sin(Math.toRadians(azimuth)),
                distance * Math.sin(Math.toRadians(elevation)),
                horizontal * Math.cos(Math.toRadians(azimuth)));
        camera.getTransforms().setAll(lookAt(eye, target));
    }

    /**
     * JavaFX cameras look along local +Z and use local +Y as screen-down, so
     * the basis is (right, -up, forward). This keeps world +Y up on screen.
     */
    private static Affine lookAt(Point3D eye, Point3D target) {
        Point3D forward = target.subtract(eye).normalize();
        Point3D right = forward.crossProduct(WORLD_UP);
        if (right.magnitude() < 1e-9) {
            right = new Point3D(1, 0, 0);
        }
        right = right.normalize();
        Point3D up = right.crossProduct(forward).normalize();
        Point3D down = up.multiply(-1);
        return new Affine(
                right.getX(), down.getX(), forward.getX(), eye.getX(),
                right.getY(), down.getY(), forward.getY(), eye.getY(),
                right.getZ(), down.getZ(), forward.getZ(), eye.getZ());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
