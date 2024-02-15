package remoteio.client.render;

/**
 * @author dmillerw
 */
public class TessellatorCapture {

    private static boolean capture = false;

    public static double rotationAngle = 0D;

    public static double offsetX = 0D;
    public static double offsetZ = 0D;

    public static void startCapturing() {
        reset();
        capture = true;
    }

    public static void reset() {
        capture = false;
        rotationAngle = 0;
        offsetX = 0;
        offsetZ = 0;
    }

    private static ThreadLocal<double[]> rotationPoint = ThreadLocal.withInitial(() -> new double[3]);
    public static double[] rotatePoint(double x, double y, double z) {
        if (capture) {
            final double radians = Math.toRadians(rotationAngle);
            final double sin = Math.sin(radians);
            final double cos = Math.cos(radians);

            double nx = x * cos - z * sin;
            double nz = x * sin + z * cos;

            x = nx;
            z = nz;
        }
        final double[] doubles = rotationPoint.get();
        doubles[0] = x;
        doubles[1] = y;
        doubles[2] = z;
        return doubles;
    }

    public static double[] rotatePointWithOffset(double x, double y, double z) {
        return rotatePointWithOffset(x, y, z, offsetX, 0, offsetZ);
    }

    private static ThreadLocal<double[]> rotationPointOffset = ThreadLocal.withInitial(() -> new double[3]);
    public static double[] rotatePointWithOffset(double x, double y, double z, double offsetX, double offsetY,
            double offsetZ) {
        if (capture) {
            final double radians = Math.toRadians(rotationAngle);
            final double sin = Math.sin(radians);
            final double cos = Math.cos(radians);

            x += offsetX;
            z += offsetZ;

            x += 0.5;
            z += 0.5;

            double nx = x * cos - z * sin;
            double nz = x * sin + z * cos;

            x = nx;
            z = nz;

            x -= offsetX;
            z -= offsetZ;

            x -= 0.5;
            z -= 0.5;
        }
        final double[] doubles = rotationPointOffset.get();
        doubles[0] = x;
        doubles[1] = y;
        doubles[2] = z;
        return doubles;
    }
}
