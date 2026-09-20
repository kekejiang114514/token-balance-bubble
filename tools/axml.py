#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AXML (Android binary XML) 解析器 + 生成器。

解析部分用于从真实 APK 中取得权威结构，
生成部分用于在没有 aapt2 的环境下手工产出合法 AndroidManifest.xml。
"""
import struct, json, sys

RES_STRING_POOL = 0x0001
RES_XML = 0x0003
RES_XML_START_NS = 0x0100
RES_XML_END_NS = 0x0101
RES_XML_START_EL = 0x0102
RES_XML_END_EL = 0x0103
RES_XML_CDATA = 0x0104
RES_XML_RESOURCE_MAP = 0x0180

SORTED_FLAG = 1 << 0
UTF8_FLAG = 1 << 8

TYPE_NULL = 0x00
TYPE_REFERENCE = 0x01
TYPE_ATTRIBUTE = 0x02
TYPE_STRING = 0x03
TYPE_FLOAT = 0x04
TYPE_DIMENSION = 0x05
TYPE_FRACTION = 0x06
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12


# ---------------------------------------------------------------- parsing

def _read_len8(buf, off):
    b = buf[off]
    if b & 0x80:
        return ((b & 0x7F) << 8) | buf[off + 1], off + 2
    return b, off + 1


def _read_len16(buf, off):
    v = struct.unpack_from('<H', buf, off)[0]
    if v & 0x8000:
        v = ((v & 0x7FFF) << 16) | struct.unpack_from('<H', buf, off + 2)[0]
        return v, off + 4
    return v, off + 2


def parse_string_pool(buf, off):
    ctype, hsize, size = struct.unpack_from('<HHI', buf, off)
    assert ctype == RES_STRING_POOL, hex(ctype)
    count, style_count, flags, strings_start, styles_start = struct.unpack_from('<IIIII', buf, off + 8)
    utf8 = bool(flags & UTF8_FLAG)
    offsets = struct.unpack_from('<%dI' % count, buf, off + hsize) if count else ()
    base = off + strings_start
    strings = []
    for o in offsets:
        p = base + o
        if utf8:
            n_chars, p = _read_len8(buf, p)
            n_bytes, p = _read_len8(buf, p)
            strings.append(buf[p:p + n_bytes].decode('utf-8', 'replace'))
        else:
            n_chars, p = _read_len16(buf, p)
            strings.append(buf[p:p + n_chars * 2].decode('utf-16-le', 'replace'))
    return {
        'flags': flags, 'utf8': utf8, 'strings': strings,
        'styles': [], 'header_size': hsize, 'size': size,
        'strings_start': strings_start, 'styles_start': styles_start,
    }, size


def parse_axml(data):
    ctype, hsize, total = struct.unpack_from('<HHI', data, 0)
    assert ctype == RES_XML, hex(ctype)
    out = {'pool': None, 'resmap': [], 'items': [], 'total': total, 'header_size': hsize}
    off = hsize
    while off < total:
        t, hs, sz = struct.unpack_from('<HHI', data, off)
        line, comment = struct.unpack_from('<II', data, off + 8) if hs >= 16 else (0, 0)
        if t == RES_STRING_POOL:
            out['pool'], _ = parse_string_pool(data, off)
        elif t == RES_XML_RESOURCE_MAP:
            n = (sz - hs) // 4
            out['resmap'] = list(struct.unpack_from('<%dI' % n, data, off + hs)) if n else []
        elif t == RES_XML_START_NS:
            prefix_i, uri_i = struct.unpack_from('<ii', data, off + hs)
            out['items'].append({'k': 'start_ns', 'prefix': prefix_i, 'uri': uri_i, 'line': line, 'comment': comment})
        elif t == RES_XML_END_NS:
            prefix_i, uri_i = struct.unpack_from('<ii', data, off + hs)
            out['items'].append({'k': 'end_ns', 'prefix': prefix_i, 'uri': uri_i, 'line': line, 'comment': comment})
        elif t == RES_XML_START_EL:
            ns_i, name_i = struct.unpack_from('<ii', data, off + hs)
            attr_start, attr_size, attr_count = struct.unpack_from('<HHH', data, off + hs + 8)
            id_idx, class_idx, style_idx = struct.unpack_from('<HHH', data, off + hs + 14)
            p = off + hs + attr_start
            attrs = []
            for _i in range(attr_count):
                a_ns, a_name, a_raw = struct.unpack_from('<iii', data, p)
                a_vsize, a_res0, a_type, a_data = struct.unpack_from('<HBBI', data, p + 12)
                attrs.append({'ns': a_ns, 'name': a_name, 'raw': a_raw,
                              'type': a_type, 'data': a_data})
                p += attr_size
            out['items'].append({'k': 'start', 'ns': ns_i, 'name': name_i,
                                 'id_idx': id_idx, 'class_idx': class_idx,
                                 'style_idx': style_idx, 'attrs': attrs, 'line': line, 'comment': comment})
        elif t == RES_XML_END_EL:
            ns_i, name_i = struct.unpack_from('<ii', data, off + hs)
            out['items'].append({'k': 'end', 'ns': ns_i, 'name': name_i, 'line': line, 'comment': comment})
        elif t == RES_XML_CDATA:
            d_i = struct.unpack_from('<i', data, off + hs)[0]
            out['items'].append({'k': 'cdata', 'data': d_i})
        else:
            out['items'].append({'k': 'unknown', 'type': hex(t), 'size': sz})
        off += sz
    return out


def dump_axml(ax):
    """人类可读的层级输出"""
    pool = ax['pool']['strings']
    def S(i):
        return pool[i] if 0 <= i < len(pool) else '<%d>' % i
    lines = ['STRING_POOL flags=0x%x count=%d' % (ax['pool']['flags'], len(pool))]
    for i, s in enumerate(pool):
        rid = ax['resmap'][i] if i < len(ax['resmap']) else 0
        lines.append('  [%2d] %-40r res=0x%08x' % (i, s, rid))
    depth = 0
    for it in ax['items']:
        if it['k'] == 'start_ns':
            lines.append('%s ns %s=%s' % ('  ' * depth, S(it['prefix']), S(it['uri'])))
        elif it['k'] == 'start':
            lines.append('%s<%s' % ('  ' * depth, S(it['name'])))
            for a in it['attrs']:
                lines.append('%s   @%s:%s raw=%s type=0x%02x data=0x%x(%s)' % (
                    '  ' * depth, S(a['ns']) or '∅', S(a['name']), S(a['raw']) if a['raw'] >= 0 else 'nil',
                    a['type'], a['data'],
                    S(a['data']) if a['type'] == TYPE_STRING and a['data'] < len(pool) else a['data']))
            lines.append('%s  >' % ('  ' * depth))
            depth += 1
        elif it['k'] == 'end':
            depth -= 1
            lines.append('%s</%s>' % ('  ' * depth, S(it['name'])))
    return '\n'.join(lines)


if __name__ == '__main__':
    with open(sys.argv[1], 'rb') as f:
        ax = parse_axml(f.read())
    print(dump_axml(ax))
