package com.shambanexa.common;

import com.shambanexa.dto.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public final class BoundaryGeometry {

    private static final double SAME = 1e-6;

    private BoundaryGeometry() {
    }

    /** Empty input is allowed. A partial or crossed ring returns a message. */
    public static String problem(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() > 60) {
            return "A boundary can have at most 60 corners.";
        }
        List<double[]> ring = new ArrayList<>();
        for (GeoPoint point : points) {
            if (point == null || point.latitude() == null || point.longitude() == null) {
                return "Each corner needs a latitude and a longitude.";
            }
            double latitude = point.latitude().doubleValue();
            double longitude = point.longitude().doubleValue();
            if (latitude < -90 || latitude > 90) {
                return "Enter a latitude between -90 and 90.";
            }
            if (longitude < -180 || longitude > 180) {
                return "Enter a longitude between -180 and 180.";
            }
            if (latitude < -12.2 || latitude > -0.7 || longitude < 29.0 || longitude > 40.7) {
                return "Choose a point inside Tanzania.";
            }
            ring.add(new double[] { latitude, longitude });
        }
        if (ring.size() > 1 && near(ring.get(0), ring.get(ring.size() - 1))) {
            ring.remove(ring.size() - 1);
        }
        if (ring.size() < 3) {
            return "Add at least 3 corners to close the boundary.";
        }
        for (int index = 0; index < ring.size(); index++) {
            if (near(ring.get(index), ring.get((index + 1) % ring.size()))) {
                return "Corner " + (index + 1) + " repeats the next corner.";
            }
        }
        if (Math.abs(area(ring)) < 1e-12) {
            return "These corners do not enclose an area.";
        }
        if (crosses(ring)) {
            return "The boundary crosses itself. Reorder the corners so the outline does not overlap.";
        }
        return null;
    }

    private static boolean near(double[] left, double[] right) {
        return Math.abs(left[0] - right[0]) < SAME && Math.abs(left[1] - right[1]) < SAME;
    }

    private static double area(List<double[]> ring) {
        double sum = 0;
        for (int index = 0; index < ring.size(); index++) {
            double[] current = ring.get(index);
            double[] next = ring.get((index + 1) % ring.size());
            sum += current[1] * next[0] - next[1] * current[0];
        }
        return sum / 2;
    }

    private static boolean crosses(List<double[]> ring) {
        int count = ring.size();
        for (int first = 0; first < count; first++) {
            for (int second = first + 1; second < count; second++) {
                int firstEnd = (first + 1) % count;
                int secondEnd = (second + 1) % count;
                if (firstEnd == second || first == secondEnd || firstEnd == secondEnd) {
                    continue;
                }
                if (proper(ring.get(first), ring.get(firstEnd), ring.get(second), ring.get(secondEnd))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean proper(double[] a, double[] b, double[] c, double[] d) {
        double one = orient(a, b, c);
        double two = orient(a, b, d);
        double three = orient(c, d, a);
        double four = orient(c, d, b);
        if (one == 0 && two == 0 && three == 0 && four == 0) {
            return overlap(a, b, c, d);
        }
        return one * two < 0 && three * four < 0;
    }

    private static double orient(double[] start, double[] end, double[] point) {
        double value = (end[1] - start[1]) * (point[0] - start[0]) - (end[0] - start[0]) * (point[1] - start[1]);
        return Math.abs(value) < 1e-14 ? 0 : Math.signum(value);
    }

    private static boolean overlap(double[] a, double[] b, double[] c, double[] d) {
        return Math.min(a[1], b[1]) <= Math.max(c[1], d[1]) + SAME
                && Math.min(c[1], d[1]) <= Math.max(a[1], b[1]) + SAME
                && Math.min(a[0], b[0]) <= Math.max(c[0], d[0]) + SAME
                && Math.min(c[0], d[0]) <= Math.max(a[0], b[0]) + SAME;
    }
}
