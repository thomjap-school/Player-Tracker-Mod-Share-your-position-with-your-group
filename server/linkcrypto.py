"""Dependency-free authenticated obfuscation for Beacon links.

Encrypts the real relay URL into an opaque "beacon:<token>" string that only the
Player Beacon mod can decrypt (same key + scheme, see LinkCrypto.java). The
recipient can't read the host or turn it into a Tracker link.

Scheme (must match the Java side byte-for-byte):
    blob  = iv(16) || ciphertext || tag(16)
    token = "beacon:" + base64url(blob)
    keystream_j = SHA256(KEY || iv || big-endian uint32(j))   for j = 0,1,2,...
    ciphertext  = plaintext XOR keystream[:len(plaintext)]
    tag         = HMAC-SHA256(KEY, iv || ciphertext)[:16]

Security note: the real protection is the server-side role token (BEACON_KEY only
grants the write-only endpoint). This layer just hides the URL from casual users.
"""
import base64
import hashlib
import hmac
import os

# Shared 32-byte key, embedded here and in the mod (LinkCrypto.java).
KEY = base64.b64decode("75d1vwZsDJ0J3nFeKhXG7X/PSuC0fbl+z+HLvh9vilk=")
PREFIX = "beacon:"


def _keystream(iv: bytes, n: int) -> bytes:
    out = bytearray()
    j = 0
    while len(out) < n:
        out += hashlib.sha256(KEY + iv + j.to_bytes(4, "big")).digest()
        j += 1
    return bytes(out[:n])


def encrypt(plaintext: str) -> str:
    pt = plaintext.encode("utf-8")
    iv = os.urandom(16)
    ks = _keystream(iv, len(pt))
    ct = bytes(a ^ b for a, b in zip(pt, ks))
    tag = hmac.new(KEY, iv + ct, hashlib.sha256).digest()[:16]
    return PREFIX + base64.urlsafe_b64encode(iv + ct + tag).decode()


def decrypt(token: str) -> str:
    if token.startswith(PREFIX):
        token = token[len(PREFIX):]
    blob = base64.urlsafe_b64decode(token)
    iv, ct, tag = blob[:16], blob[16:-16], blob[-16:]
    exp = hmac.new(KEY, iv + ct, hashlib.sha256).digest()[:16]
    if not hmac.compare_digest(exp, tag):
        raise ValueError("bad tag")
    ks = _keystream(iv, len(ct))
    return bytes(a ^ b for a, b in zip(ct, ks)).decode("utf-8")


if __name__ == "__main__":
    import sys
    if len(sys.argv) >= 2:
        print(encrypt(sys.argv[1]))
