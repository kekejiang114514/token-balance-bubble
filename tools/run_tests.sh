#!/bin/sh
# 纯逻辑单测：只编译不依赖 Android 的类，在 JVM 上直接跑，不需要设备。
# RigModel 是 PartRig 的常量来源，必须一起编译（漏了会报 cannot find symbol D_*）。
set -e
cd "$(dirname "$0")/.."

OUT=out/testclasses
rm -rf $OUT
mkdir -p $OUT

javac -nowarn -encoding UTF-8 -source 8 -target 8 -d $OUT \
  app/src/com/coco/balancebubble/Currencies.java \
  app/src/com/coco/balancebubble/Presets.java \
  app/src/com/coco/balancebubble/PetAction.java \
  app/src/com/coco/balancebubble/PetTalk.java \
  app/src/com/coco/balancebubble/TextWrap.java \
  app/src/com/coco/balancebubble/Theme.java \
  app/src/com/coco/balancebubble/BubbleLayout.java \
  app/src/com/coco/balancebubble/RigModel.java \
  app/src/com/coco/balancebubble/PartRig.java \
  test/SettingsKitTest.java \
  test/CurrencyTest.java test/PetTalkTest.java test/TextWrapTest.java \
  test/RigAnim.java test/PartRigTest.java test/RigGoldenTest.java test/RigAnimTest.java

failed=0
for t in SettingsKitTest CurrencyTest PetTalkTest TextWrapTest PartRigTest RigGoldenTest RigAnimTest; do
  echo "==================== $t ===================="
  if ! java -cp $OUT $t; then
    failed=1
  fi
done

if [ $failed -eq 0 ]; then
  echo "==================== 全部测试通过 ===================="
else
  echo "==================== 有测试失败 ===================="
  exit 1
fi
