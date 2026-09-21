#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 App 的 AndroidManifest.xml（二进制 AXML）"""
import sys, os
sys.path.insert(0, '/root/build')
import axml_build as B

TYPE_STR = 0x03
TYPE_REF = 0x01
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_BOOL = 0x12

A = B.Attr
E = B.Elem

ANDROID_NS = 'http://schemas.android.com/apk/res/android'
THEME_NO_ACTIONBAR = 0x0103012c   # @android:style/Theme.DeviceDefault.Light.NoActionBar

CONFIG_CHANGES = 0x04A0           # orientation|screenSize|keyboardHidden
SOFT_INPUT_ADJUST_RESIZE = 0x10
ICON_REF = 0x7F010000             # @mipmap/ic_launcher（见 arsc_build.py）


def an(name, vtype, value):
    return A('android', name, vtype, value)


def main():
    activity = E('activity', attrs=[
        an('name', TYPE_STR, '.MainActivity'),
        an('exported', TYPE_BOOL, True),
        an('theme', TYPE_REF, THEME_NO_ACTIONBAR),
        an('windowSoftInputMode', TYPE_INT_HEX, SOFT_INPUT_ADJUST_RESIZE),
        an('configChanges', TYPE_INT_HEX, CONFIG_CHANGES),
    ], children=[
        E('intent-filter', attrs=[], children=[
            E('action', attrs=[an('name', TYPE_STR, 'android.intent.action.MAIN')]),
            E('category', attrs=[an('name', TYPE_STR, 'android.intent.category.LAUNCHER')]),
        ]),
    ])

    service = E('service', attrs=[
        an('name', TYPE_STR, '.BubbleService'),
        an('exported', TYPE_BOOL, False),
    ])

    receiver = E('receiver', attrs=[
        an('name', TYPE_STR, '.BootReceiver'),
        an('exported', TYPE_BOOL, True),
        an('enabled', TYPE_BOOL, True),
    ], children=[
        E('intent-filter', attrs=[], children=[
            E('action', attrs=[an('name', TYPE_STR, 'android.intent.action.BOOT_COMPLETED')]),
            E('action', attrs=[an('name', TYPE_STR, 'android.intent.action.QUICKBOOT_POWERON')]),
        ]),
    ])

    app = E('application', attrs=[
        an('label', TYPE_STR, '鲸鱼娘桌宠'),
        an('icon', TYPE_REF, ICON_REF),
        an('roundIcon', TYPE_REF, ICON_REF),
        an('allowBackup', TYPE_BOOL, False),
        an('supportsRtl', TYPE_BOOL, True),
        an('usesCleartextTraffic', TYPE_BOOL, False),
        an('hardwareAccelerated', TYPE_BOOL, True),
    ], children=[activity, service, receiver])

    root = E('manifest', attrs=[
        A(None, 'package', TYPE_STR, 'com.coco.balancebubble'),
        an('versionCode', TYPE_INT_DEC, 5),
        an('versionName', TYPE_STR, '1.4'),
    ], children=[
        E('uses-sdk', attrs=[
            an('minSdkVersion', TYPE_INT_DEC, 23),
            an('targetSdkVersion', TYPE_INT_DEC, 29),
        ]),
        E('uses-permission', attrs=[an('name', TYPE_STR, 'android.permission.INTERNET')]),
        E('uses-permission', attrs=[an('name', TYPE_STR, 'android.permission.SYSTEM_ALERT_WINDOW')]),
        E('uses-permission', attrs=[an('name', TYPE_STR, 'android.permission.FOREGROUND_SERVICE')]),
        E('uses-permission', attrs=[an('name', TYPE_STR, 'android.permission.RECEIVE_BOOT_COMPLETED')]),
        app,
    ])

    ids = B.load_attr_ids('/root/build/public.xml')
    data = B.build([root], [('android', ANDROID_NS)], ids, {'android': 1})
    out = sys.argv[1] if len(sys.argv) > 1 else '/root/build/out/AndroidManifest.xml'
    with open(out, 'wb') as f:
        f.write(data)
    sys.stderr.write('manifest written: %d bytes\n' % len(data))
    return 0


if __name__ == '__main__':
    sys.exit(main())

