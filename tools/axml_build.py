#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AXML 生成器：在没有 aapt2 的环境下手工产出合法的 AndroidManifest.xml。

编码约定由真实 aapt2 产物的二进制逆向得到：
  * 字符串池 UTF-16、flags=0、headerSize=28
  * 字符串池前段 = 用到的 android 命名空间属性名，按资源 ID 升序（两段之间不去重）
  * 资源映射表长度 = 属性名个数，与字符串下标一一对应
  * 其余字符串（命名空间、元素名、属性值、非 android 属性名）去重后字典序
  * 属性条目固定 20 字节（ResXMLTree_attribute）
  * 节点头 comment 字段恒为 0xffffffff
"""
import re
import struct

RES_STRING_POOL = 0x0001
RES_XML = 0x0003
RES_XML_START_NS = 0x0100
RES_XML_END_NS = 0x0101
RES_XML_START_EL = 0x0102
RES_XML_END_EL = 0x0103
RES_XML_RESOURCE_MAP = 0x0180

TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12

ANDROID_NS = 'http://schemas.android.com/apk/res/android'
NO_INDEX = 0xFFFFFFFF


def load_attr_ids(path):
    """从 AOSP public.xml 读取 属性名 -> 资源 ID"""
    ids = {}
    for m in re.finditer(r'<public type="attr" name="([^"]+)" id="(0x[0-9a-fA-F]+)"', open(path).read()):
        ids[m.group(1)] = int(m.group(2), 16)
    return ids


class Attr(object):
    __slots__ = ('ns', 'name', 'type', 'value')

    def __init__(self, ns, name, vtype, value):
        self.ns = ns
        self.name = name
        self.type = vtype
        self.value = value

    def __repr__(self):
        return 'Attr(%r,%r,0x%02x,%r)' % (self.ns, self.name, self.type, self.value)


class Elem(object):
    __slots__ = ('name', 'line', 'attrs', 'children')

    def __init__(self, name, line=1, attrs=None, children=None):
        self.name = name
        self.line = line
        self.attrs = attrs or []
        self.children = children or []


def build(elements, namespaces, attr_ids, ns_lines=None):
    """elements: 顶层元素列表; namespaces: [(prefix, uri)]"""
    attr_names = set()      # android 属性名
    other = set()           # 其余字符串

    for prefix, uri in namespaces:
        other.add(prefix)
        other.add(uri)

    def walk(e):
        other.add(e.name)
        for a in e.attrs:
            if a.ns == 'android':
                attr_names.add(a.name)
            else:
                other.add(a.name)
            if a.type == TYPE_STRING:
                other.add(a.value)
        for c in e.children:
            walk(c)
    for e in elements:
        walk(e)

    missing = sorted(n for n in attr_names if n not in attr_ids)
    if missing:
        raise KeyError('public.xml 缺少属性: %s' % missing)

    attr_block = sorted(attr_names, key=lambda n: attr_ids[n])
    rest = sorted(other)
    pool = attr_block + rest                       # 两段之间不去重（与 aapt2 一致）

    attr_index = {n: i for i, n in enumerate(attr_block)}
    rest_index = {s: len(attr_block) + i for i, s in enumerate(rest)}

    def aidx(n):        # android 属性名
        return attr_index[n]

    def ridx(s):        # 其余字符串
        return rest_index[s]

    # ---- 字符串池 ----
    data = bytearray()
    offsets = []
    for s in pool:
        offsets.append(len(data))
        data += struct.pack('<H', len(s)) + s.encode('utf-16-le') + b'\x00\x00'
    while len(data) % 4:
        data += b'\x00'

    count = len(pool)
    head_chunks = []
    head_chunks.append(
        struct.pack('<HHIIIIII', RES_STRING_POOL, 28, 28 + 4 * count + len(data),
                    count, 0, 0, 28 + 4 * count, 0) +
        (struct.pack('<%dI' % count, *offsets) if count else b'') + bytes(data))

    # ---- 资源映射表 ----
    resmap = [attr_ids[n] for n in attr_block]
    if resmap:
        head_chunks.append(struct.pack('<HHI', RES_XML_RESOURCE_MAP, 8, 8 + 4 * len(resmap)) +
                           struct.pack('<%dI' % len(resmap), *resmap))

    # ---- 命名空间 ----
    nsl = ns_lines or {}
    end_ns = []
    for prefix, uri in namespaces:
        line = nsl.get(prefix, 1)
        head_chunks.append(struct.pack('<HHII', RES_XML_START_NS, 16, 24, line) +
                           struct.pack('<I', NO_INDEX) +
                           struct.pack('<ii', ridx(prefix), ridx(uri)))
        end_ns.append((prefix, uri, line))

    # ---- 元素 ----
    body = []

    def emit(e):
        attr_bytes = b''
        for a in e.attrs:
            a_ns = ridx(ANDROID_NS) if a.ns == 'android' else -1
            a_name = aidx(a.name) if a.ns == 'android' else ridx(a.name)
            if a.type == TYPE_STRING:
                a_raw = ridx(a.value)
                a_data = ridx(a.value)
            else:
                a_raw = -1
                if a.type == TYPE_INT_BOOLEAN:
                    a_data = 0xFFFFFFFF if a.value else 0
                else:
                    a_data = a.value & 0xFFFFFFFF
            attr_bytes += struct.pack('<iiihBBI', a_ns, a_name, a_raw, 8, 0, a.type, a_data)
        body.append(struct.pack('<HHII', RES_XML_START_EL, 16, 16 + 20 + 20 * len(e.attrs), e.line) +
                    struct.pack('<I', NO_INDEX) +
                    struct.pack('<iiHHHHHH', -1, ridx(e.name), 20, 20, len(e.attrs), 0, 0, 0) +
                    attr_bytes)
        for c in e.children:
            emit(c)
        body.append(struct.pack('<HHII', RES_XML_END_EL, 16, 24, e.line) +
                    struct.pack('<I', NO_INDEX) + struct.pack('<ii', -1, ridx(e.name)))

    for e in elements:
        emit(e)

    for prefix, uri, line in reversed(end_ns):
        body.append(struct.pack('<HHII', RES_XML_END_NS, 16, 24, line) +
                    struct.pack('<I', NO_INDEX) +
                    struct.pack('<ii', ridx(prefix), ridx(uri)))

    out = b''.join(head_chunks) + b''.join(body)
    return struct.pack('<HHI', RES_XML, 8, 8 + len(out)) + out
