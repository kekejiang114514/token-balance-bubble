# -*- coding: utf-8 -*-
"""用 androguard（第三方独立实现）校验手工构建的 APK。"""
import sys
from androguard.core.apk import APK

path = sys.argv[1] if len(sys.argv) > 1 else 'out/app.apk'
a = APK(path)

print('package      :', a.get_package())
print('app name     :', a.get_app_name())
print('versionName  :', a.get_androidversion_name())
print('versionCode  :', a.get_androidversion_code())
print('min/target   :', a.get_min_sdk_version(), '/', a.get_target_sdk_version())
print('is_valid_apk :', a.is_valid_APK())
print('permissions  :', sorted(a.get_permissions()))
print('activities   :', a.get_activities())
print('services     :', a.get_services())
print('receivers    :', a.get_receivers())
print('icon path    :', a.get_app_icon())

# DEX 解析
dex = a.get_dex()
print('dex size     :', len(dex) if dex else 0)

# 资源表：取出图标资源
res = a.get_android_resources()
pkg = a.get_package()
cfg = res.get_res_configs(0x7F010000) if res else []
print('res 0x7f010000:', cfg)

# DEX：用 androguard 自带解析器列类
from androguard.core.dex import DEX
d = DEX(a.get_dex())
print('dex classes (%d):' % len(d.get_classes()))
for c in sorted(d.get_classes(), key=lambda x: x.get_name())[:12]:
    print('   ', c.get_name())

print('ALL GOOD')
