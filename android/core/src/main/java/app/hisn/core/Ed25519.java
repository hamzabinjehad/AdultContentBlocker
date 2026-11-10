package app.hisn.core;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * RFC 8032 Ed25519 verification in pure Java.
 *
 * <p>Android's platform Ed25519 provider is not dependable at minSdk 26, and a
 * downloaded blocklist must not raise the minimum API or depend on the device's
 * crypto vendor. This verifies one detached signature over exact bytes; it does
 * not generate keys, sign, or handle secrets. The public key is pinned in the
 * app and the private key never exists on this side.</p>
 */
public final class Ed25519 {
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger P = BigInteger.ONE.shiftLeft(255)
            .subtract(BigInteger.valueOf(19));
    private static final BigInteger L = BigInteger.ONE.shiftLeft(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));
    private static final BigInteger D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);
    private static final BigInteger I = TWO
            .modPow(P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), P);
    private static final BigInteger EIGHT = BigInteger.valueOf(8);
    private static final Point BASE = decode(hex(
            "5866666666666666666666666666666666666666666666666666666666666666"));

    /** One detached signature check. Any malformed input is false, never an exception. */
    public static boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
        if (publicKey == null || publicKey.length != 32 || message == null
                || signature == null || signature.length != 64) return false;
        try {
            Point a = decode(publicKey);
            Point r = decode(java.util.Arrays.copyOfRange(signature, 0, 32));
            if (a == null || r == null) return false;
            BigInteger s = little(signature, 32, 64);
            if (s.compareTo(L) >= 0) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            digest.update(signature, 0, 32);
            digest.update(publicKey);
            digest.update(message);
            BigInteger k = little(digest.digest(), 0, 64).mod(L);
            // [S]B = R + [k]A, compared projectively without re-encoding.
            Point left = multiply(BASE, s);
            Point right = add(r, multiply(a, k));
            return left.equals(right);
        } catch (RuntimeException | NoSuchAlgorithmException unavailable) {
            return false;
        }
    }

    private static final class Point {
        final BigInteger x, y, z, t;

        Point(BigInteger x, BigInteger y, BigInteger z, BigInteger t) {
            this.x = x; this.y = y; this.z = z; this.t = t;
        }

        boolean equals(Point other) {
            // Cross-multiply: x/z and y/z are equal without a modular inverse.
            return x.multiply(other.z).mod(P).equals(other.x.multiply(z).mod(P))
                    && y.multiply(other.z).mod(P).equals(other.y.multiply(z).mod(P));
        }
    }

    private static Point decode(byte[] encoded) {
        if (encoded == null || encoded.length != 32) return null;
        byte[] copy = encoded.clone();
        int sign = (copy[31] >> 7) & 1;
        copy[31] &= 0x7f;
        BigInteger y = little(copy, 0, 32);
        if (y.compareTo(P) >= 0) return null;
        BigInteger y2 = y.multiply(y).mod(P);
        BigInteger u = y2.subtract(BigInteger.ONE).mod(P);
        BigInteger v = D.multiply(y2).add(BigInteger.ONE).mod(P);
        BigInteger x = sqrtRatio(u, v);
        if (x == null) return null;
        if (x.testBit(0) != (sign == 1)) x = P.subtract(x).mod(P);
        if (x.signum() == 0 && sign == 1) return null;
        return new Point(x, y, BigInteger.ONE, x.multiply(y).mod(P));
    }

    /** sqrt(u/v), or null when the ratio is not a square. p = 5 mod 8. */
    private static BigInteger sqrtRatio(BigInteger u, BigInteger v) {
        BigInteger v3 = v.multiply(v).mod(P).multiply(v).mod(P);
        BigInteger v7 = v3.multiply(v3).mod(P).multiply(v).mod(P);
        BigInteger pow = u.multiply(v3).mod(P)
                .multiply(u.multiply(v7).mod(P).modPow(P.subtract(BigInteger.valueOf(5))
                        .divide(EIGHT), P)).mod(P);
        BigInteger x = pow;
        BigInteger check = v.multiply(x).mod(P).multiply(x).mod(P);
        if (check.equals(u.mod(P))) return x;
        BigInteger neg = P.subtract(u).mod(P);
        if (check.equals(neg)) return x.multiply(I).mod(P);
        return null;
    }

    private static Point add(Point a, Point b) {
        BigInteger aa = a.y.subtract(a.x).mod(P).multiply(b.y.subtract(b.x).mod(P)).mod(P);
        BigInteger bb = a.y.add(a.x).mod(P).multiply(b.y.add(b.x).mod(P)).mod(P);
        BigInteger cc = a.t.multiply(b.t).mod(P).multiply(D).mod(P).multiply(TWO).mod(P);
        BigInteger dd = a.z.multiply(b.z).mod(P).multiply(TWO).mod(P);
        BigInteger e = bb.subtract(aa).mod(P);
        BigInteger f = dd.subtract(cc).mod(P);
        BigInteger g = dd.add(cc).mod(P);
        BigInteger h = bb.add(aa).mod(P);
        return new Point(e.multiply(f).mod(P), g.multiply(h).mod(P),
                f.multiply(g).mod(P), e.multiply(h).mod(P));
    }

    private static Point doublePoint(Point a) {
        BigInteger aa = a.x.multiply(a.x).mod(P);
        BigInteger bb = a.y.multiply(a.y).mod(P);
        BigInteger cc = a.z.multiply(a.z).mod(P).multiply(TWO).mod(P);
        BigInteger dd = aa.negate().mod(P);
        BigInteger e = a.x.add(a.y).mod(P).multiply(a.x.add(a.y).mod(P)).subtract(aa)
                .subtract(bb).mod(P);
        BigInteger g = dd.add(bb).mod(P);
        BigInteger f = g.subtract(cc).mod(P);
        BigInteger h = dd.subtract(bb).mod(P);
        return new Point(e.multiply(f).mod(P), g.multiply(h).mod(P),
                f.multiply(g).mod(P), e.multiply(h).mod(P));
    }

    private static Point multiply(Point point, BigInteger scalar) {
        Point result = new Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO);
        Point addend = point;
        BigInteger n = scalar;
        while (n.signum() > 0) {
            if (n.testBit(0)) result = add(result, addend);
            addend = doublePoint(addend);
            n = n.shiftRight(1);
        }
        return result;
    }

    private static BigInteger little(byte[] bytes, int from, int to) {
        byte[] copy = java.util.Arrays.copyOfRange(bytes, from, to);
        for (int i = 0; i < copy.length / 2; i++) {
            byte tmp = copy[i]; copy[i] = copy[copy.length - 1 - i];
            copy[copy.length - 1 - i] = tmp;
        }
        return new BigInteger(1, copy);
    }

    private static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private Ed25519() { }
}
