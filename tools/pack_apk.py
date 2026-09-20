#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""手工打包 APK（Android 兼容：无 data descriptor、无 zip64、stored 条目 4 字节对齐）。

用法：
    pack_apk.py out.apk [z|s]name=path ...
        z: DEFLATE 压缩（dex / xml 用）
        s: 原样存储（png / resources.arsc 用，顺便 4 字节对齐）
"""
import sys, os, zlib, struct


def pack(entries, out_apk):
    with open(out_apk, 'wb') as f:
        central = []
        offset = 0
        for name, path, compress in entries:
            data = open(path, 'rb').read()
            nb = name.encode('utf-8')
            crc = zlib.crc32(data) & 0xffffffff
            if compress:
                co = zlib.compressobj(9, zlib.DEFLATED, -15)
                payload = co.compress(data) + co.flush()
                method = 8
            else:
                payload = data
                method = 0

            base = offset + 30 + len(nb)
            pad = 0 if compress else (-base) % 4
            if pad:
                # extra 字段最小 4 字节（id+size），不够就再补一整轮
                elen = pad if pad >= 4 else pad + 4
                extra = struct.pack('<HH', 0xD935, elen) + b'\x00' * (elen - 4)
            else:
                extra = b''

            hdr_off = offset
            local = struct.pack('<IHHHHHIIIHH', 0x04034b50, 20, 0, method, 0, 0,
                                crc, len(payload), len(data), len(nb), len(extra))
            f.write(local + nb + extra + payload)
            offset = hdr_off + len(local) + len(nb) + len(extra) + len(payload)
            central.append((nb, method, crc, len(payload), len(data), hdr_off))

        cd_off = offset
        for nb, method, crc, csize, usize, hdr_off in central:
            f.write(struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 20, 20, 0, method,
                                0, 0, crc, csize, usize, len(nb), 0, 0, 0, 0, 0,
                                hdr_off) + nb)
            offset += 46 + len(nb)
        cd_size = offset - cd_off
        f.write(struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, len(central), len(central),
                            cd_size, cd_off, 0))


def parse(spec):
    comp = True
    if spec[:2] in ('z:', 's:'):
        comp = spec[0] == 'z'
        spec = spec[2:]
    name, _, path = spec.partition('=')
    return (name, path, comp)


def main():
    out = sys.argv[1]
    entries = [parse(a) for a in sys.argv[2:]]
    pack(entries, out)
    print('apk packed: %s (%d bytes, %d entries)' % (out, os.path.getsize(out), len(entries)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
