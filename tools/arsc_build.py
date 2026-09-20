"""生成 Android resources.arsc（单包 / 单类型 / 单条目），格式对齐 aapt2。"""
import struct
import sys

RES_STRING_POOL = 0x0001
RES_TABLE = 0x0002
RES_TABLE_PACKAGE = 0x0200
RES_TABLE_TYPE = 0x0201
RES_TABLE_TYPE_SPEC = 0x0202

UTF8_FLAG = 1 << 8
CONFIG_SIZE = 64
PKG_HEADER_SIZE = 288
TYPE_HEADER_SIZE = 20 + CONFIG_SIZE


def enc_len8(v):
    if v > 0x7F:
        return bytes([0x80 | (v >> 8), v & 0xFF])
    return bytes([v])


def enc_len16(v):
    if v > 0x7FFF:
        return struct.pack('<HH', 0x8000 | (v >> 16), v & 0xFFFF)
    return struct.pack('<H', v)


def build_string_pool(strings, utf8=True):
    """返回完整的 ResStringPool chunk。"""
    count = len(strings)
    offsets = []
    data = bytearray()
    if utf8:
        for s in strings:
            b = s.encode('utf-8')
            offsets.append(len(data))
            # 先字符数（UTF-16 单元），再字节数
            data += enc_len8(len(s.encode('utf-16-le')) // 2) + enc_len8(len(b)) + b + b'\x00'
        flags = UTF8_FLAG
    else:
        for s in strings:
            units = s.encode('utf-16-le')
            offsets.append(len(data))
            data += enc_len16(len(units) // 2) + units + b'\x00\x00'
        flags = 0

    str_start = 28 + 4 * count
    pad = (-str_start) % 4
    str_start += pad
    total = str_start + len(data)
    total_pad = (-total) % 4
    total += total_pad

    out = bytearray()
    out += struct.pack('<HHIIIIII', RES_STRING_POOL, 28, total, count, 0, flags, str_start, 0)
    for o in offsets:
        out += struct.pack('<I', o)
    out += b'\x00' * pad
    out += data
    out += b'\x00' * total_pad
    assert len(out) == total, (len(out), total)
    return bytes(out)


def build_config(density=0):
    cfg = bytearray(CONFIG_SIZE)
    struct.pack_into('<I', cfg, 0, CONFIG_SIZE)
    if density:
        struct.pack_into('<H', cfg, 14, density)
    return bytes(cfg)


def build_arsc(package_name, type_name, res_name, file_path, density=0, pkg_id=0x7F):
    """单资源：type_name/res_name -> file_path（如 mipmap/ic_launcher -> res/mipmap/ic_launcher.png）"""
    global_pool = build_string_pool([file_path], utf8=True)
    type_pool = build_string_pool([type_name], utf8=False)
    key_pool = build_string_pool([res_name], utf8=True)

    # ---- TYPE_SPEC ----
    spec = bytearray()
    spec += struct.pack('<HHIBBHI', RES_TABLE_TYPE_SPEC, 16, 16 + 4, 1, 0, 0, 1)
    spec += struct.pack('<I', 0)

    # ---- TYPE ----
    entry = struct.pack('<HHI', 8, 0, 0)                      # ResTable_entry: key = keyPool[0]
    entry += struct.pack('<HBBI', 8, 0, 0x03, 0)              # Res_value: TYPE_STRING -> globalPool[0]
    entries_start = TYPE_HEADER_SIZE + 4                      # header + 1 个偏移
    type_size = entries_start + len(entry)
    typ = bytearray()
    typ += struct.pack('<HHIBBHII', RES_TABLE_TYPE, TYPE_HEADER_SIZE, type_size, 1, 0, 0, 1, entries_start)
    typ += build_config(density)
    typ += struct.pack('<I', 0)                               # entry offset
    typ += entry

    # ---- PACKAGE ----
    ts_off = PKG_HEADER_SIZE
    ks_off = ts_off + len(type_pool)
    pkg_size = ks_off + len(key_pool) + len(spec) + len(typ)

    hdr = bytearray(PKG_HEADER_SIZE)
    struct.pack_into('<HHII', hdr, 0, RES_TABLE_PACKAGE, PKG_HEADER_SIZE, pkg_size, pkg_id)
    nm = package_name.encode('utf-16-le')
    assert len(nm) <= 256
    hdr[12:12 + len(nm)] = nm
    struct.pack_into('<IIIII', hdr, 268, ts_off, 0, ks_off, 0, 0)

    pkg = bytes(hdr) + type_pool + key_pool + bytes(spec) + bytes(typ)
    assert len(pkg) == pkg_size

    # ---- TABLE ----
    total = 12 + len(global_pool) + len(pkg)
    root = struct.pack('<HHII', RES_TABLE, 12, total, 1)
    return root + global_pool + pkg


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'resources.arsc'
    data = build_arsc('com.coco.balancebubble', 'mipmap', 'ic_launcher',
                      'res/mipmap/ic_launcher.png', density=0)
    with open(out, 'wb') as f:
        f.write(data)
    sys.stderr.write('arsc written: %d bytes\n' % len(data))
    return 0


if __name__ == '__main__':
    sys.exit(main())
