public class VectorUtils {
    public static double[] vecAddition(double[] a, double[] b) {
        return new double[] {
                a[0] + b[0],
                a[1] + b[1],
                a[2] + b[2]
        };
    }

    public static double[] crossProduct(double[] a, double[] b) {
        return new double[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    public static double[] vecMultiplication(double[] a, double k) {
        return new double[] {
                a[0] * k,
                a[1] * k,
                a[2] * k
        };
    }

    public static double[] normalize(double[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector cannot be null or empty");
        }

        // Calculate magnitude (length) of vector
        double magnitude = 0.0;
        for (double component : vector) {
            magnitude += component * component;
        }
        magnitude = Math.sqrt(magnitude);

        // Check if vector is already normalized
        if (magnitude == 1) {
            return vector;
        }

        // Check if vector is a zero vector
        if (magnitude == 0) {
            throw new IllegalArgumentException("Cannot normalize a zero vector");
        }

        // Create normalized vector
        double[] normalized = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / magnitude;
        }

        return normalized;
    }
}
