package thomjap.playerbeacon.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Decrypts an obfuscated "beacon:&lt;token&gt;" link into the real relay URL.
 *
 * <p>The link handed out is an encrypted blob; only this mod (with the embedded
 * key) can turn it back into a {@code wss://…/r/<beaconKey>} URL. A recipient
 * can't read the host nor derive the Tracker link. The real security is
 * server-side (the beacon token only grants the write-only role); this layer
 * just hides the URL.
 *
 * <p>Scheme (must match {@code server/linkcrypto.py} byte-for-byte):
 * <pre>
 *   blob  = iv(16) || ciphertext || tag(16)
 *   token = "beacon:" + base64url(blob)
 *   keystream_j = SHA-256(KEY || iv || big-endian uint32(j))   for j = 0,1,2,…
 *   ciphertext  = plaintext XOR keystream[:len]
 *   tag         = HMAC-SHA256(KEY, iv || ciphertext)[:16]
 * </pre>
 */
public final class LinkCrypto {
	/** Shared 32-byte key, mirrored in {@code server/linkcrypto.py}. */
	private static final byte[] KEY = {
			-17, -105, 117, -65, 6, 108, 12, -99, 9, -34, 113, 94, 42, 21, -58, -19,
			127, -49, 74, -32, -76, 125, -71, 126, -49, -31, -53, -66, 31, 111, -118, 89
	};
	public static final String PREFIX = "beacon:";

	private LinkCrypto() {
	}

	private static byte[] sha256(byte[] a) throws Exception {
		return MessageDigest.getInstance("SHA-256").digest(a);
	}

	private static byte[] keystream(byte[] iv, int n) throws Exception {
		byte[] out = new byte[n];
		int off = 0;
		int j = 0;
		while (off < n) {
			ByteBuffer bb = ByteBuffer.allocate(KEY.length + iv.length + 4);
			bb.put(KEY).put(iv).putInt(j);
			byte[] block = sha256(bb.array());
			int c = Math.min(block.length, n - off);
			System.arraycopy(block, 0, out, off, c);
			off += c;
			j++;
		}
		return out;
	}

	private static byte[] hmac(byte[] data) throws Exception {
		Mac m = Mac.getInstance("HmacSHA256");
		m.init(new SecretKeySpec(KEY, "HmacSHA256"));
		return m.doFinal(data);
	}

	/**
	 * Decodes a {@code beacon:…} token into the real URL. Any non-token string
	 * (e.g. a plain {@code ws://…}) is returned unchanged, as is a tampered token
	 * (which then simply won't connect).
	 */
	public static String decode(String s) {
		if (s == null || !s.startsWith(PREFIX)) {
			return s;
		}
		try {
			byte[] blob = Base64.getUrlDecoder().decode(s.substring(PREFIX.length()));
			byte[] iv = Arrays.copyOfRange(blob, 0, 16);
			byte[] ct = Arrays.copyOfRange(blob, 16, blob.length - 16);
			byte[] tag = Arrays.copyOfRange(blob, blob.length - 16, blob.length);
			byte[] ivct = new byte[iv.length + ct.length];
			System.arraycopy(iv, 0, ivct, 0, iv.length);
			System.arraycopy(ct, 0, ivct, iv.length, ct.length);
			if (!MessageDigest.isEqual(Arrays.copyOf(hmac(ivct), 16), tag)) {
				return s;
			}
			byte[] ks = keystream(iv, ct.length);
			byte[] pt = new byte[ct.length];
			for (int i = 0; i < ct.length; i++) {
				pt[i] = (byte) (ct[i] ^ ks[i]);
			}
			return new String(pt, StandardCharsets.UTF_8);
		} catch (Exception e) {
			return s;
		}
	}
}
