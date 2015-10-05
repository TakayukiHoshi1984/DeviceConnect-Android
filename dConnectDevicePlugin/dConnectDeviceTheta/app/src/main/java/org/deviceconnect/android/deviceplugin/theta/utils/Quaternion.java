/*
 Quaternion.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.theta.utils;


public final class Quaternion {

    private Quaternion() {
    }

    public static void rotate(final float[] target, final float[] rotation, final float[] result) {
        multiply(
            target[0], target[1], target[2], target[3],
            rotation[0], -1 * rotation[1], -1 * rotation[2], -1 * rotation[3],
            result);
        multiply(
            rotation[0], rotation[1], rotation[2], rotation[3],
            result[0], result[1], result[2], result[3],
            result);
    }

    public static void multiply(final float[] a, final float b[], final float[] result) {
        multiply(a[0], a[1], a[2], a[3], b[0], b[1], b[2], b[3], result);
    }

    public static void multiply(final float a0, final float a1, final float a2, final float a3,
                                final float b0, final float b1, final float b2, final float b3,
                                final float[] result) {
        // Real part:
        result[0] = a0 * b0 - (a1 * b1 + a2 * b2 + a3 * b3);

        // Imaginary part:
        result[1] = a0 * b1 + b0 * a1 + (a2 * b3 - a3 * b2);
        result[2] = a0 * b2 + b0 * a2 + (a3 * b1 - a1 * b3);
        result[3] = a0 * b3 + b0 * a3 + (a1 * b2 - a2 * b1);
    }
}
