# 鲸鱼娘桌宠

一个常驻桌面的 Android 悬浮角色。可以当桌宠养着——她会挥手、眨眼、甩尾巴，点一下说句卖萌话；也可以开成余额查询器，点一下就看到 API 账户还剩多少钱；两个一起来就是混合模式。

支持 DeepSeek、硅基流动、Moonshot、OpenRouter、OpenAI 以及任意 OpenAI 兼容的自定义接口。

## 特性

- **三种模式**：仅 token 查询 / 仅桌宠 / 混合模式，设置页里切换，改完立刻生效
- **角色动作**：整体姿态把立绘当骨架，绕脚底支点倾斜、摇摆、挤压拉伸，跳跃时脚下的影子会一起缩放；内置漂浮、蹦跳、摇摆、歪头、摇头、点头、转圈、伸懒腰、缩一下、弹一下、打瞌睡、甩尾、挥手 13 种动作
- **局部形变**：在整体姿态之上再叠一层 24×24 段（25×25 个顶点）的网格形变，14 个驱动量分别管头部旋转/平移、三束发丝、尾鳍、两条手臂、两只手掌、呼吸和风——每 2.2~6 秒眨一次眼、头顶与两侧长发随风漂移、甩尾时两条长发朝同一侧摆开、挥手时右臂绕肩点转动并把右手推到 16px 外、呼吸会把胸口和颈根一起抬起来；脸和手是保护区，不会被发丝拖走
- **说话配动作**：每句语料都绑定了动作，说到「一起去看海吧？」会漂起来，说到「呜哇！吓到了吗？」会弹一下；平时待机也会每隔几秒自己动一动
- **悬浮角色**：`TYPE_APPLICATION_OVERLAY` 常驻窗口，不占用状态栏，不干扰其他应用
- **随手拖动**：拖到任意位置，松手自动记住，重启后仍在原处；窗口尺寸变化或旋转屏幕时会自动收回屏幕内
- **点一下才说话**：气泡平时收起，点角色才弹出来，说完自动收回；长按打开设置，拖动时不会误触发
- **刷新有反馈**：查余额时气泡里先显示「正在刷新中…」，再换成金额
- **气泡宽度跟着字数走**：短句是小气泡，长句自动折行、气泡变宽变高，折不下才缩字号
- **定时刷新**：间隔可调（1 分钟 ~ 12 小时），按你关心的粒度；定时刷新是静默的，不会弹气泡
- **余额变化提醒**：混合模式下后台静默刷新发现金额变了，会主动冒个泡提醒
- **角色可关**：不想要角色就关掉，只留一个气泡；动作也能单独关掉省电
- **15 种货币**：下拉选择，默认人民币；支持自动识别接口返回的币种，也能自定义符号
- **开机自启**：按上次的状态恢复，不需要每次手动打开
- **零依赖**：不使用 AndroidX、Kotlin、Gradle，全部代码化布局，产物不到 500 KB
- **密钥本地存放**：API Key 只存在应用私有目录，只有查询请求会发给服务商
- **应用内检查更新**：设置页「关于」分页里可以查有没有新版本，查到能直接去下载或打开 Release 页面；不想要的版本可以「跳过这个版本」，之后不再提醒。每天最多自动查一次（GitHub 匿名接口有额度限制），手动点不受限制

## 支持的服务商

| 服务商 | 余额接口 | 默认币种 | 备注 |
|---|---|---|---|
| DeepSeek | `/user/balance` | CNY | 官方接口，返回 `balance_infos[].total_balance` |
| 硅基流动 | `/v1/user/info` | CNY | 读取 `data.balance` |
| Moonshot | `/v1/users/me/balance` | CNY | 读取 `data.available_balance` |
| OpenRouter | `/api/v1/credits` | USD | 读取 `data.total_credits` |
| OpenAI | `/v1/dashboard/billing/credit_grants` | USD | 需要较老的 Key 才有权限 |
| 自定义 | 自填 | 自填 | 任意 OpenAI 兼容接口，可自定认认证头与取值路径 |

预设的接口地址、路径、取值字段在 `app/src/com/coco/balancebubble/Presets.java` 里，想加一家改这个文件即可。

## 下载安装

APK 可以直接从 **[Releases 页面](https://github.com/kekejiang114514/token-balance-bubble/releases)** 下载，也可以点下面表格里的链接，或者从 [`releases/`](releases) 目录取。下载后直接安装（需要允许「安装未知来源应用」）。

| 版本 | 文件 | 大小 | SHA-256 |
|---|---|---|---|
| v1.10.1 | [balance-bubble-1.10.1.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.10.1/balance-bubble-1.10.1.apk) | 304 KB | `067c7d4c…d285b` |
| v1.10 | [balance-bubble-1.10.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.10/balance-bubble-1.10.apk) | 304 KB | `84325386…7f6d` |
| v1.9 | [balance-bubble-1.9.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.9/balance-bubble-1.9.apk) | 304 KB | `529e538b…430b` |
| v1.8 | [balance-bubble-1.8.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.8/balance-bubble-1.8.apk) | 304 KB | `494f7566…0598` |
| v1.7 | [balance-bubble-1.7.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.7/balance-bubble-1.7.apk) | 300 KB | `840c2f70…7a4c` |
| v1.5 | [balance-bubble-1.5.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.5/balance-bubble-1.5.apk) | 281 KB | `bf9066b6…09f0` |
| v1.4 | [balance-bubble-1.4.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.4/balance-bubble-1.4.apk) | 277 KB | `0a56b3c1…33a8` |
| v1.3 | [balance-bubble-1.3.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.3/balance-bubble-1.3.apk) | 469 KB | `5c27986b…1b39` |
| v1.2 | [balance-bubble-1.2.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.2/balance-bubble-1.2.apk) | 469 KB | `b9fc8022…d960` |
| v1.1 | [balance-bubble-1.1.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.1/balance-bubble-1.1.apk) | 457 KB | `02d59b7f…8e0f` |
| v1.0 | [balance-bubble-1.0.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.0/balance-bubble-1.0.apk) | 40 KB | `1f2e2455…ccd6` |

安装前建议核对完整校验和：

```sh
sha256sum balance-bubble-1.10.1.apk
# 067c7d4c100069ef966bc3666358480b3f7f1ba785270519d5dc3b69144d285b
```

**版本关系**：以下版本的包名和签名相同，可以直接覆盖安装，设置和 API Key 都会保留（v1.6 只出过内部构建、没有发布）。如果只想用最新版，装 v1.10.1 就行。

## 使用

1. 打开应用，选择你的服务商（接口地址会自动填好）
2. 粘贴 API Key（在服务商官网的「API Keys」页面创建）
3. 点「测试连接」，确认能读到余额
4. 点「保存并显示气泡」，按提示授予「显示在其他应用上层」权限

之后回到桌面就能看到角色（气泡平时是收起的）：

| 操作 | 效果 |
|---|---|
| 拖动角色 | 移动位置，松手后记住 |
| 单击角色 | 混合模式与仅 token 查询模式：刷新余额并把气泡弹出来；仅桌宠模式：随机说句卖萌话＋做动作 |
| 单击气泡 | 立刻把气泡收起来 |
| 长按角色 | 打开设置界面 |
| 点通知 | 打开设置界面 |

**三种模式**（在设置页「③ 模式与显示」里切换，改完立刻生效）：

| 模式 | 点一下角色 | 自动行为 |
|---|---|---|
| 仅 token 查询 | 气泡里先显示「正在刷新中…」，再显示余额 | 按设定间隔静默刷新余额 |
| 仅桌宠 | 随机说一句卖萌话，并做配套动作 | 每隔一分钟自动说一句＋做动作 |
| 混合模式（默认） | 刷新余额并弹出气泡，顺带做个动作 | 两者都在跑；后台刷新发现金额变了会主动冒泡提醒 |

**角色的动作**在设置页「③ 模式与显示」里有独立开关，关掉就静态站立。角色贴图是 `assets/char.png`（立绘抠掉白底后的透明 PNG），整体姿态用的是整张贴图：绕脚底支点旋转（歪头、摇头、摇摆）、缩放（伸懒腰、缩一下、落地挤压）、上下位移（漂浮、蹦跳），起跳时地面上的影子会同步缩小，落下来再压扁一下。

在这层整体姿态之上，还有一层**局部形变**：贴图被切成 24×24 段的网格（25×25 个顶点），用一组权重场驱动 14 个量——头部旋转/平移、三束发丝、尾鳍、两条手臂、两只手掌、呼吸、眨眼、风。于是不用把立绘拆成零件，也能做出眨眼（每 2.2~6 秒一次，眼睑纵向压下去而眼睛不左右挪）、发丝随风（三束按不同相位的正弦漂移）、甩尾（两条后发朝同一侧摆开）、挥手（右臂绕肩点转动，手掌再被单独推出去）、呼吸（胸口和颈根一起起伏）。脸和手是保护区，权重场在那里被压到 0，发丝摆动不会把脸一起拖走。

> 说清楚代价：这仍是**单张平面立绘**，局部形变是网格形变，不是真正的分部件动画。它不会画出被遮住的部分（比如转过去的手臂背面），幅度也不能太大，否则边缘会出现拉伸感。要做到那一步，得把角色拆成头、身、手臂、尾巴等分离图层，再按骨骼挂上去。动作随时可以关掉，关掉后角色就是静态站立。

## 界面说明

设置页顶部常驻**实时预览**（和桌面上的悬浮窗共用同一套渲染代码，改什么立刻能看到），下面分成四个分页，共 36 张卡片，每张卡片用一句话说明「调这个会怎样」：

| 分页 | 放什么 |
|---|---|
| **连接** | 服务商选择、接口地址、API Key、查询间隔、目标金额与提醒、余额的正负图标 |
| **气泡** | 气泡宽度与字号、行数上限、内边距与圆角、底色与文字色、不透明度、尖角、显示/收起时长、是否显示名称和币种代码 |
| **角色** | 显示哪块贴图、大小、位置、动作开关（眨眼 / 发丝随风 / 甩尾 / 挥手 / 呼吸）、待机几分钟换个动作、不透明度与翻转 |
| **关于** | 当前版本、检查更新、打开发布页、应用信息、恢复默认参数 |

分页栏固定在屏幕底部，滚到哪儿都在；每个分页会记住自己上次滚到的位置，切回来不会跳回顶部。

不确定的参数（Base URL、余额接口路径、金额字段路径、认证头名称与前缀、自定义符号、金额后补币种代码）都放在对应分页里，卡片上都写了「填错会看到什么报错」。改坏了可以到「关于」分页点恢复默认参数。

## 币种

下拉框内置 15 种货币，默认人民币（CNY），显示为「人民币 CNY ¥」这种三段式，不用记代码：

人民币、美元、欧元、英镑、日元、港币、新台币、韩元、新加坡元、澳元、加元、印度卢比、卢布、泰铢、马来西亚林吉特。

另外两项：

- **自动识别**：采用接口返回的 `currency` 字段（如 DeepSeek 返回 `CNY`）
- **自定义符号**：自己填符号，适合没有货币符号的站点

## 隐私与安全

- **API Key 存在应用私有目录**（`/data/data/com.coco.balancebubble/shared_prefs/`），其他应用无法读取
- **请求只发往你配置的接口地址**，没有中转服务器，没有统计上报，没有广告 SDK
- 应用只有四个权限：`INTERNET`、`SYSTEM_ALERT_WINDOW`、`FOREGROUND_SERVICE`、`RECEIVE_BOOT_COMPLETED`
- 建议在服务商后台给这个 Key 设置额度上限；本应用只做只读查询，不会消耗推理额度
- 仓库里的 release APK 用一个**不包含在仓库中**的密钥签名。如果你自己从源码构建，得到的签名不同，无法覆盖安装官方 APK，需要先卸载

## 从源码构建

不需要 Android Studio，也不需要 Gradle。命令行一条命令搞定：

```sh
sh tools/fetch-deps.sh    # 下载 android.jar / r8.jar（约 30 MB）
sh tools/build.sh         # 产物在 out/app.apk
```

需要 JDK 17 和 Python 3。首次构建前还要准备签名密钥：

```sh
keytool -genkeypair -keystore keys/release.p12 -storetype PKCS12 \
        -alias release -keyalg RSA -keysize 2048 -validity 10000
echo '你的口令' > keys/storepass.txt
```

构建流程分六步：`javac` → `d8`（生成 DEX）→ 生成 `resources.arsc` → 生成二进制 `AndroidManifest.xml` → Python 打包 → `apksigner` 签名。

### 关于构建方式

这个项目没有用 Gradle，也没有用 aapt2。原因是构建环境是一个没有 Android SDK 的 Alpine/aarch64 容器，Termux 的 bionic 二进制跑不起来。所以工具链里的这两件事是手写实现的：

- **`tools/axml_build.py`** —— 生成二进制 `AndroidManifest.xml`。字符串池 UTF-16、android 属性按资源 ID 升序排列并配 `RES_XML_RESOURCE_MAP`、属性条目 20 字节，编码细节与 aapt2 产物做过字节级对照
- **`tools/arsc_build.py`** —— 生成 `resources.arsc`，把 `mipmap/ic_launcher` 注册到资源表，manifest 里的 `android:icon` 用 `0x7F010000` 引用

如果你在标准 Android SDK 环境里构建，这两步完全可以换成 aapt2 —— 但那就需要把布局资源也改成 XML，目前项目是有意全部用代码写布局的。

`tools/verify_apk.py` 用第三方库 [androguard](https://github.com/androguard/androguard) 独立校验产物（`pip install androguard`），不是自己验自己：

```sh
python3 tools/verify_apk.py out/app.apk
```

## 目录结构

```
app/
  src/com/coco/balancebubble/
    MainActivity.java    设置界面（四个分页 / 36 张卡片 / 顶部实时预览）
    BubbleService.java   前台服务 + 悬浮窗管理 + 拖拽与位置持久化 + 监听设置变化
    BubbleView.java      气泡绘制（圆角矩形 + 下方尖角 + 文字自适应与尺寸动画）
    PetView.java         角色贴图渲染：支点变换、动作播放、影子、网格形变合成
    PetAction.java       动作清单与时长（纯逻辑，可单测）
    PetTalk.java         桌宠卖萌语料（纯逻辑，可单测）
    PartRig.java         局部形变：24×24 网格（625 顶点）+ 14 个驱动量（眨眼/发丝/甩尾/挥手/呼吸，纯逻辑可单测）
    RigModel.java        骨架常量（由 tools/gen_rig_java.py 从 tools/rig_math.py 生成，别手改）
    BubbleLayout.java    气泡排版（换行、行宽、圆角与尖角几何，纯逻辑可单测）
    BubbleStyle.java     设置项 → 绘制参数的单通道转换
    TextWrap.java        中英混排断行（纯逻辑，可单测）
    Theme.java           主题色板与深浅模式（纯逻辑，可单测对比度）
    Ui.java              代码化布局控件工厂（卡片/按钮/开关/滑杆/色板/分段控件）
    Viewport.java        视口算术：滚动位置钳位、预览高度（纯逻辑，可单测）
    Version.java         版本号解析与比大小（纯逻辑，可单测）
    UpdateChecker.java   检查 GitHub Releases 有没有新版本
    BalanceApi.java      HTTP 请求、JSON 解析、字段自动识别、错误归类
    Prefs.java           设置项读写
    Presets.java         服务商预设
    Currencies.java      货币表与符号规则
    BootReceiver.java    开机自启
  assets/char.png        角色贴图（立绘抠白底后的透明 PNG，pngquant 量化）
  res/mipmap/ic_launcher.png  应用图标（同一张立绘居中排版）
tools/                   构建脚本、骨架模型与验收套件
  rig_math.py            骨架模型的唯一事实来源（骨表、权重场、幅度上限、姿态表）
  gen_rig_java.py        由 rig_math.py 生成 app/src 里的 RigModel.java
  verify_rig.py          骨架验收套件（七节量化判据 + 黄金表 + 动画时间线）
  audit_ui.py            源码级审计：查「造了控件但没挂进视图树」这类只在真机才暴露的 bug
  build.sh               切图/量化/生成 manifest/打包/签名，一键出 APK
  axml_build.py arsc_build.py manifest_gen.py pack_apk.py   无 SDK 构建链的四步
  verify_apk.py          用 androguard 独立校验产物
test/SettingsKitTest.java  主题对比度、气泡排版硬约束、币种代号往返的纯逻辑单测
test/CurrencyTest.java     币种与预设的纯逻辑单测
test/PetTalkTest.java      桌宠语料库与动作绑定的纯逻辑单测
test/TextWrapTest.java     气泡断行的纯逻辑单测
test/ViewportTest.java     滚动钳位与预览高度的纯逻辑单测
test/VersionTest.java      tag 解析与版本比较的纯逻辑单测
test/PartRigTest.java      网格形变的纯逻辑单测（权重场、保护区、连续性、稳定性）
test/RigGoldenTest.java    Java 侧读黄金表逐点比对骨架常量
test/RigAnimTest.java      Java 侧按真实时间线播放动画并落盘采样（给验收套件用）
test/rig_golden.txt        黄金表：39 个状态 ×169 个采样点的位移与权重
test/rig_anim_samples.txt  动画采样：1927 帧 ×14 个驱动量
tools/run_tests.sh         一键跑全部单测 + 控件挂载审计
releases/                  各版本已签名的 APK（v1.0~v1.5、v1.7、v1.8、v1.9、v1.10、v1.10.1）
```

## 测试

币种表、语料库、断行算法都是不依赖 Android 的纯逻辑，可以用 JVM 直接跑单测，不需要设备：

```sh
sh tools/run_tests.sh
```

覆盖：

- **币种与预设**：币种表完整性、符号前缀/后缀分支、代码大小写与别名（`RMB` → `CNY`）、自动识别与自定义分支，以及每个服务商预设声明的币种都在币种表里
- **语料与动作**：语料库非空、无空句、无重复、句子不超长、随机抽 2000 次无连续重复、1000 次能覆盖全部语料；每句话都绑定了动作，动作不是 IDLE，且每个动作的播放时长都大于 0
- **气泡断行**：放得下就不折、中文逐字断、英文按词断不切单词、标点不落行首、超出行数上限时末行加省略号，以及空文本、全空白、超长单词等边界
- **网格形变**：网格尺寸与顶点数、权重场取值域与平滑度（无硬边）、脸与手是保护区（不会被发丝拖走）、眨眼只作用在眼部且半闭是一半位移、挥手时右手摆动而腕点几乎不动、甩尾时两侧长发同向摆动且越靠根部越小、形变连续性（网格不折叠、不夸张拉伸）以及 240 帧连续播放的数值稳定性
- **设置页与主题**：主题色板在 6 种颜色 × 浅/深两档下的文字对比度（按 WCAG 3:1 判）、气泡排版硬约束（宽度/行数/字号/每行行宽，未截断时文字不丢）、币种代号与下标往返；视口算术（滚动位置钳位、预览高度随角色尺寸变化）
- **版本号**：tag 解析（`v1.7` / `1.7-rc1` → `1.7`）、分段比大小（`1.10` 比 `1.9` 新、缺段补 0）、垃圾输入一律不判定为新版
- **控件挂载审计**：`tools/audit_ui.py` 扫源码，找「造了控件但没挂进视图树」——这类 bug 编译能过、单测测不到，只有真跑界面才看得见（v1.10 修的那 13 条滑杆就是这么坏的）
- **骨架验收**：单测之外还有一套 `python3 tools/verify_rig.py`（需要 Python 3 + numpy + Pillow），七节量化判据把 Python 侧模型和 Java 侧实现拉到一起对照——磁盘上的 `RigModel.java` 必须与生成器输出逐字节一致；14 个驱动量在 39 个状态 ×169 个采样点上的位移与 Java 逐点比对；轮廓内逐点检查局部伸缩（∈[0.55, 1.80]）、剪切（≤0.80）、渲染网格单元面积（≥0.25×）与单点位移（≤125px）；脸芯、颈根这些保护区不能被别的场拖走；每个动作都要有看得见的位移且不许串台；最后按 1927 帧的时间线逐帧复算，连最紧的一帧也要留出形变余量

发布出去的 APK 与仓库源码是等价的：把对应 tag 的 `app/src` 取出来重新编译、转 dex，得到的 `classes.dex` 与发布 APK 里的逐字节相同（v1.7 / v1.8 / v1.9 / v1.10 都验过）。

## 版本历史

见 [CHANGELOG.md](CHANGELOG.md)。简要来说：

- **v1.10.1** —— 桌宠语录库从 26 句扩充到 50 句，新增的 24 句都绑定了动作；除语料数组外没有任何代码改动
- **v1.10** —— 修掉「所有参数都调不了」的根因：`Ui.sliderRow()` 造出了 `SeekBar` 却没 `addView` 进父容器，13 条滑杆全都没显示、也无法拖动（自 v1.7 设置页重做起就存在）；新增源码级审计 `tools/audit_ui.py` 专门拦「造了控件但没挂进视图树」
- **v1.9** —— 修切页签/切回应用弹回顶部（重画后等布局量完再还原滚动位置），页签栏改为固定在屏幕底部；预览里的角色尺寸、显隐、动作跟滑杆实时变；滑杆拖动不再被外层滚动抢手势
- **v1.8** —— 应用内检查更新（设置页「关于」里可检查、可打开发布页、可跳过某个版本）；修 `UpdateChecker` 漏写包声明导致的编译失败；悬浮窗自己监听设置变化，启动改用 `startForegroundService`
- **v1.7** —— 设置页重做为四个分页（连接 / 气泡 / 角色 / 关于）36 张卡片，顶部常驻实时预览；同时修复挥手时手掌前缘被压扁（最紧一帧的局部伸缩 0.45 → 0.63）、呼吸只带走肚子而胸口和脖子不动（胸口 1.8 → 3.65px、颈根 0 → 0.67px）、眨眼时左眼横向漂移 10px（v1.6 是内部构建，没有发布，改动并入本条）
- **v1.5** —— 加上网格局部形变：眨眼、发丝随风、甩尾、挥手，脸和手是保护区；待机动作池扩到 10 种（动作共 13 种）
- **v1.4** —— 角色换成原始立绘（自动抠白底）并加上整体骨架动作（漂浮、蹦跳、摇摆、转圈等 11 种，说话配动作），应用图标一并换成鲸鱼娘；气泡宽度跟着字数走、长句自动折行；模式细分为仅 token 查询 / 仅桌宠 / 混合模式，混合模式会在余额变化时主动提醒
- **v1.3** —— 新增「token 查询模式」开关与桌宠模式（点角色随机说卖萌话、每分钟自动说一句），应用更名「鲸鱼娘桌宠」；气泡改为点击才弹出、7 秒后自动收起；查询余额时显示「正在刷新中…」
- **v1.2** —— 界面重做为卡片式（每个参数都有说明、带实时预览、高级设置默认折叠）；币种改为 15 种货币的下拉框，默认人民币；修复拖动后位置被重置的 bug
- **v1.1** —— 应用更名为「token 余额查询器」，内置角色贴图与桌面图标，扩展为六家服务商预设，金额路径与币种支持自动识别
- **v1.0** —— 首个可用版本，支持 DeepSeek 余额查询与悬浮显示

> 说明：v1.0 和 v1.1 只保留了签名后的 APK，源码快照没有留存；仓库中的 `app/src` 是 v1.10.1 的代码（v1.6 起的骨架模型也在其中）。每个版本的源码都可以在对应的 tag 上取到：`git checkout v1.10.1`。

## 已知限制

- 在 Android 10（EMUI）上开发验证，Android 11+ 的悬浮窗行为差异没有实测过
- 部分国产 ROM 需要额外信任应用、手动开启「后台弹出界面」「自启动」权限，否则气泡可能在清理后台时被回收
- OpenAI 的余额接口对新版 Key 已不开放，可能返回 401
- 界面为中文，暂未做多语言
- 角色动画是持续重绘，虽然限了 30fps 且在窗口不可见时停止，但长时间挂着仍会比静态角色多耗一点电；可以在设置里关掉「角色动作」
- 角色是单张平面立绘，局部形变靠网格变形模拟，遮住的部分画不出来，幅度也有上限（见上文「角色的动作」）
- 抠图用的是「与白底不连通的容差填充」，如果以后换成立绘背景不是纯白，或者角色内部有和背景同色又连通到边缘的区域，需要重新调参
- 没有真机截图回归测试：验证靠单测 + 源码审计 + 用无障碍服务读屏/手势做少量端到端检查（Android 11 以下还不能用无障碍接口截图），拖拽手感之类的交互细节仍需实际使用才能确认

## 常见问题

**气泡不见了？**
先看通知栏有没有「token 余额运行中」。如果服务还在但看不到气泡，多半是悬浮窗权限被系统收回了，到系统设置里重新授权。

**提示 401 / 403？**
API Key 复制不完整、带了空格，或者没有调用余额接口的权限。到服务商官网重新生成一个试试。

**提示 404？**
高级设置里的「余额接口路径」填错了。选回预设服务商会自动填对。

**显示的是人民币但我想看美元？**
「显示与刷新」卡片里的币种下拉换成美元即可。

**能从源码自己构建吗？**
可以，见上面的「从源码构建」。注意自己构建的 APK 签名与官方版不同，不能覆盖安装。

## 许可证

[MIT](LICENSE)

本项目与服务商官方无关，不是任何一家公司的官方产品。使用时请遵守各服务商的接口条款。
