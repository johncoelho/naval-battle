package br.com.navalbattle.data

/**
 * Utilidades de criptografia puras em Kotlin, compartilhadas pelo login com Google (PKCE)
 * e pelo login com a Apple (nonce). Sem cinterop com CommonCrypto, de propósito.
 */

private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** Base64url sem padding (RFC 4648 §5) — escrito à mão para não depender de versão do stdlib. */
internal fun base64UrlEncode(bytes: ByteArray): String = buildString {
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        val triple = (b0 shl 16) or (b1 shl 8) or b2
        append(BASE64_URL_ALPHABET[(triple shr 18) and 0x3F])
        append(BASE64_URL_ALPHABET[(triple shr 12) and 0x3F])
        if (i + 1 < bytes.size) append(BASE64_URL_ALPHABET[(triple shr 6) and 0x3F])
        if (i + 2 < bytes.size) append(BASE64_URL_ALPHABET[triple and 0x3F])
        i += 3
    }
}

/** SHA-256 puro Kotlin (RFC 6234) — só usado para o `code_challenge` do PKCE; evita cinterop com CommonCrypto. */
private val SHA256_K = uintArrayOf(
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u
)

internal fun sha256(message: ByteArray): ByteArray {
    val h = uintArrayOf(
        0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
        0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u
    )

    val bitLength = message.size.toULong() * 8uL
    val paddingZeros = ((56 - (message.size + 1) % 64) + 64) % 64
    val padded = ByteArray(message.size + 1 + paddingZeros + 8)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[padded.size - 1 - i] = ((bitLength shr (8 * i)) and 0xFFuL).toByte()
    }

    val w = IntArray(64)
    var chunkStart = 0
    while (chunkStart < padded.size) {
        for (i in 0 until 16) {
            val base = chunkStart + i * 4
            w[i] = ((padded[base].toInt() and 0xFF) shl 24) or
                ((padded[base + 1].toInt() and 0xFF) shl 16) or
                ((padded[base + 2].toInt() and 0xFF) shl 8) or
                (padded[base + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val w15 = w[i - 15].toUInt()
            val w2 = w[i - 2].toUInt()
            val s0 = w15.rotateRight(7) xor w15.rotateRight(18) xor (w15 shr 3)
            val s1 = w2.rotateRight(17) xor w2.rotateRight(19) xor (w2 shr 10)
            w[i] = (w[i - 16].toUInt() + s0 + w[i - 7].toUInt() + s1).toInt()
        }

        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]

        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = hh + s1 + ch + SHA256_K[i] + w[i].toUInt()
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            hh = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }

        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh

        chunkStart += 64
    }

    val result = ByteArray(32)
    for (i in 0 until 8) {
        result[i * 4] = (h[i] shr 24).toByte()
        result[i * 4 + 1] = (h[i] shr 16).toByte()
        result[i * 4 + 2] = (h[i] shr 8).toByte()
        result[i * 4 + 3] = h[i].toByte()
    }
    return result
}

private const val HEX_LOWER = "0123456789abcdef"

/** SHA-256 em hexadecimal minúsculo — o formato que a Apple pede para o `nonce` do pedido. */
internal fun sha256Hex(message: ByteArray): String = buildString {
    for (byte in sha256(message)) {
        val v = byte.toInt() and 0xFF
        append(HEX_LOWER[v ushr 4])
        append(HEX_LOWER[v and 0xF])
    }
}
