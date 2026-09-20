import struct
import sys

RES_STRING_POOL = 0x0001
RES_TABLE = 0x0002
RES_TABLE_PACKAGE = 0x0200
RES_TABLE_TYPE = 0x0201
RES_TABLE_TYPE_SPEC = 0x0202


def parse_pool(d, off, want_strings=False):
    ct, ch, cs = struct.unpack_from('<HHI', d, off)
    assert ct == RES_STRING_POOL, hex(ct)
    count, style_count, flags, str_start, style_start = struct.unpack_from('<IIIII', d, off + 8)
    utf8 = bool(flags & (1 << 8))
    offsets = [struct.unpack_from('<I', d, off + ch + 4 * i)[0] for i in range(count)]
    strings = []
    if want_strings:
        for o in offsets:
            p = off + str_start + o
            if utf8:
                # len 为 (u16,u16) 解析
                n1 = d[p]
                p2 = p + 1
                if n1 & 0x80:
                    n = ((n1 & 0x7f) << 8) | d[p2]
                    p2 += 1
                else:
                    n = n1
                # 跳过字节长度
                l1 = d[p2]
                p2 += 1
                if l1 & 0x80:
                    ln = ((l1 & 0x7f) << 8) | d[p2]
                    p2 += 1
                else:
                    ln = l1
                strings.append(d[p2:p2 + ln].decode('utf-8', 'replace'))
            else:
                n = struct.unpack_from('<H', d, p)[0]
                p += 2
                if n & 0x8000:
                    n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', d, p)[0]
                    p += 2
                strings.append(d[p:p + n * 2].decode('utf-16-le', 'replace'))
    return {'off': off, 'header': ch, 'size': cs, 'count': count, 'styles': style_count,
            'flags': flags, 'utf8': utf8, 'str_start': str_start, 'strings': strings,
            'end': off + cs}


def dump(path, only_types=3):
    d = open(path, 'rb').read()
    print('file size', len(d))
    ct, ch, cs = struct.unpack_from('<HHI', d, 0)
    pkg_count = struct.unpack_from('<I', d, 8)[0]
    print('root type=0x%04x headerSize=%d size=%d packages=%d' % (ct, ch, cs, pkg_count))
    off = ch
    # 全局字符串池
    pool = parse_pool(d, off, want_strings=True)
    print('global pool: count=%d utf8=%s flags=0x%x size=%d' % (
        pool['count'], pool['utf8'], pool['flags'], pool['size']))
    print('  sample:', pool['strings'][:6])
    off = pool['end']

    while off < len(d) - 7:
        ct, ch, cs = struct.unpack_from('<HHI', d, off)
        if ct != RES_TABLE_PACKAGE:
            print('unexpected chunk 0x%04x at %d' % (ct, off))
            break
        pid = struct.unpack_from('<I', d, off + 8)[0]
        name = d[off + 12:off + 12 + 256].decode('utf-16-le').split('\x00')[0]
        ts_off, lpt, ks_off, lpk = struct.unpack_from('<IIII', d, off + 268)
        print('\nPKG id=0x%x name=%s headerSize=%d size=%d' % (pid, name, ch, cs))
        print('  typeStrings@+%d lastPublicType=%d keyStrings@+%d lastPublicKey=%d' % (
            ts_off, lpt, ks_off, lpk))
        tsp = parse_pool(d, off + ts_off, want_strings=True)
        print('  typeStrings: count=%d %r' % (tsp['count'], tsp['strings'][:14]))
        ksp = parse_pool(d, off + ks_off, want_strings=True)
        print('  keyStrings : count=%d %r' % (ksp['count'], ksp['strings'][:6]))

        # 遍历 type spec / type
        p = off + ch
        # 跳过两个字符串池
        p = tsp['end']
        if ksp['off'] > tsp['off']:
            p = max(p, ksp['end'])
        shown = 0
        types = []
        while p < off + cs - 7:
            t, h, sz = struct.unpack_from('<HHI', d, p)
            if t == RES_TABLE_TYPE_SPEC:
                tid, res0, res1, ecount = struct.unpack_from('<BBHI', d, p + 8)
                flags = [struct.unpack_from('<I', d, p + h + 4 * i)[0] for i in range(ecount)]
                types.append(('spec', tid, ecount, flags[:4]))
            elif t == RES_TABLE_TYPE:
                tid, flags, ecount, estart = struct.unpack_from('<BBHI', d, p + 8)
                cfg_size = struct.unpack_from('<I', d, p + 16)[0]
                cfg = d[p + 16:p + 16 + cfg_size]
                types.append(('type', tid, ecount, cfg_size, estart, flags))
            elif t == 0x0203:
                types.append(('lib',))
            else:
                types.append(('?0x%04x' % t,))
            p += sz
        # 打印前 N 个 type 详情
        for info in types:
            if info[0] == 'type' and shown < only_types:
                tid, flags, ecount, estart, tflags = info[1], info[5], info[2], info[4], info[5]
                print('  TYPE id=%d entries=%d entriesStart=%d flags=0x%x cfgSize=%d' % (
                    tid, ecount, estart, info[5], info[3]))
                shown += 1
        print('  chunk summary:', types[:16])
        off += cs
    return 0


if __name__ == '__main__':
    sys.exit(dump(sys.argv[1] if len(sys.argv) > 1 else 'ref/resources.arsc'))
