"""Rewrite a WAV (any common source format, e.g. IEEE float mono as served by the
Hugging Face datasets-server) to 16 kHz mono PCM16 so the JVM tracer reader accepts it."""
import struct
import sys


def die(msg: str):
    print(f"wav_pcm16: FAIL: {msg}", file=sys.stderr)
    sys.exit(1)


def main():
    if len(sys.argv) != 3:
        die("usage: wav_pcm16.py <in.wav> <out.wav>")
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, "rb") as f:
        raw = f.read()
    if len(raw) < 44 or raw[:4] != b"RIFF" or raw[8:12] != b"WAVE":
        die(f"not a RIFF/WAVE file: {src}")
    pos = 12
    fmt = None
    data_offset = data_len = None
    while pos + 8 <= len(raw):
        cid = raw[pos:pos + 4]
        size = struct.unpack("<I", raw[pos + 4:pos + 8])[0]
        start = pos + 8
        if cid == b"fmt ":
            fmt = raw[start:start + size]
        elif cid == b"data":
            data_offset, data_len = start, size
        pos = start + size + (size & 1)
    if fmt is None or data_offset is None:
        die(f"missing fmt/data chunk: {src}")
    if len(fmt) < 16:
        die(f"short fmt chunk: {src}")
    audio_format, channels, rate = struct.unpack("<HHI", fmt[:8])
    bits = struct.unpack("<H", fmt[14:16])[0]
    if audio_format == 0xFFFE and len(fmt) >= 40:
        sub_fmt = fmt[24 + 2:24 + 18]
        audio_format = sub_fmt[0:2][0]
    if audio_format not in (1, 3, 0xFFFE):
        die(f"unsupported wav format {audio_format}: {src}")
    if channels != 1 or rate != 16000:
        die(f"expected 16 kHz mono, got rate={rate} channels={channels}: {src}")
    bytes_per_sample = bits // 8
    body = raw[data_offset:data_offset + data_len]
    n = len(body) // bytes_per_sample
    out = bytearray()
    if audio_format == 1:
        if bits != 16:
            die(f"unsupported PCM bit width {bits}: {src}")
        out += body[: n * 2]
    else:
        if bits != 32:
            die(f"unsupported float bit width {bits}: {src}")
        for i in range(n):
            v = struct.unpack("<f", body[i * 4:(i + 1) * 4])[0]
            s = max(-1.0, min(1.0, v))
            out += struct.pack("<h", int(round(s * 32767.0)))
    with open(dst, "wb") as f:
        f.write(b"RIFF")
        f.write(struct.pack("<I", 36 + len(out)))
        f.write(b"WAVEfmt ")
        f.write(struct.pack("<IHHIIHH", 16, 1, 1, 16000, 32000, 2, 16))
        f.write(b"data")
        f.write(struct.pack("<I", len(out)))
        f.write(out)
    print(f"wav_pcm16: {dst} ({n} samples, {n / 16000:.2f} s)")


if __name__ == "__main__":
    main()
