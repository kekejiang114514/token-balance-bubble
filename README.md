# 鲸鱼娘桌宠

一个常驻桌面的 Android 悬浮角色。可以当桌宠养着——她会挥手、眨眼、甩尾巴，点一下说句卖萌话；也可以开成余额查询器，点一下就看到 API 账户还剩多少钱；两个一起来就是混合模式。

支持 DeepSeek、硅基流动、Moonshot、OpenRouter、OpenAI 以及任意 OpenAI 兼容的自定义接口。

## 特性

- **三种模式**：仅 token 查询 / 仅桌宠 / 混合模式，设置页里切换，改完立刻生效
- **角色动作**：把角色立绘当成整体骨架，绕脚底支点倾斜、摇摆、挤压拉伸，跳跃时脚下的影子会一起缩放；内置漂浮、蹦跳、摇摆、歪头、摇头、转圈、伸懒腰、缩一下、弹一下、打瞌睡 10 种动作
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
| v1.4 | [balance-bubble-1.4.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.4/balance-bubble-1.4.apk) | 277 KB | `0a56b3c1…33a8` |
| v1.3 | [balance-bubble-1.3.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.3/balance-bubble-1.3.apk) | 469 KB | `5c27986b…1b39` |
| v1.2 | [balance-bubble-1.2.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.2/balance-bubble-1.2.apk) | 469 KB | `b9fc8022…d960` |
| v1.1 | [balance-bubble-1.1.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.1/balance-bubble-1.1.apk) | 457 KB | `02d59b7f…8e0f` |
| v1.0 | [balance-bubble-1.0.apk](https://github.com/kekejiang114514/token-balance-bubble/releases/download/v1.0/balance-bubble-1.0.apk) | 40 KB | `1f2e2455…ccd6` |

安装前建议核对完整校验和：

```sh
sha256sum balance-bubble-1.4.apk
# 0a56b3c17460e3108053b66797912504753f609431a922ab8bf4a635ed0033a8
```

**版本关系**：五个版本的包名和签名相同，可以直接覆盖安装，设置和 API Key 都会保留。如果只想用最新版，装 v1.4 就行。

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

**角色的动作**在设置页「③ 模式与显示」里有独立开关，关掉就静态站立。角色贴图是 `assets/char.png`（立绘抠掉白底后的透明 PNG），动作用的是整张贴图：绕脚底支点旋转（歪头、摇头、摇摆）、缩放（伸懒腰、缩一下、落地挤压）、上下位移（漂浮、蹦跳），起跳时地面上的影子会同步缩小，落下来再压扁一下。

> 这里要说清楚：这是**单张平面立绘**，没有分部件，所以做不了「挥手」「眨眼」这种需要肢体独立运动的动作。要做到那一步，得把角色拆成头、身、手臂、尾巴等分离图层，再按骨骼挂上去。动作随时可以关掉。

## 界面说明

设置页分成四张编号卡片，按顺序走一遍就能用起来。每个输入框下面都写了这个参数是干什么的、填错会看到什么报错。

**高级设置默认折叠**，收进去的是这些东西，不确定就别展开：

| 参数 | 作用 |
|---|---|
| 显示名称 | 气泡上那行小字，留空则只显示金额 |
| Base URL | 接口域名，自定义服务商才需要改 |
| 余额接口路径 | 返回 404 时多半是这里不对 |
| 金额字段路径 | 留空则自动识别；形如 `balance_infos.0.total_balance` |
| 认证头名称 / 前缀 | 默认 `Authorization: Bearer `，少数服务商不同 |
| 自定义符号 | 币种选「自定义符号」时使用 |
| 金额后补币种代码 | 打开后显示成 `33.83 CNY`，用于区分同为 ¥ 的人民币和日元 |
| 把气泡放回默认位置 | 拖到找不回来的地方时用 |
| 恢复默认参数 | 一键还原 |

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
    MainActivity.java    设置界面（四张卡片 + 高级区 + 实时预览）
    BubbleService.java   前台服务 + 悬浮窗管理 + 拖拽与位置持久化
    BubbleView.java      气泡绘制（圆角矩形 + 下方尖角 + 文字自适应与尺寸动画）
    PetView.java         角色贴图渲染：支点变换、动作播放、影子
    PetAction.java       动作清单与时长（纯逻辑，可单测）
    TextWrap.java        中英混排断行（纯逻辑，可单测）
    BalanceApi.java      HTTP 请求、JSON 解析、字段自动识别、错误归类
    Prefs.java           设置项读写
    Presets.java         服务商预设
    Currencies.java      货币表与符号规则
    Ui.java              代码化布局控件工厂
    BootReceiver.java    开机自启
  assets/char.png        角色贴图（立绘抠白底后的透明 PNG，pngquant 量化）
  res/mipmap/ic_launcher.png  应用图标（同一张立绘居中排版）
  res/mipmap/            应用图标
tools/                   构建与校验脚本
test/CurrencyTest.java   币种与预设的纯逻辑单测
test/PetTalkTest.java    桌宠语料库与动作绑定的纯逻辑单测
test/TextWrapTest.java   气泡断行的纯逻辑单测
tools/run_tests.sh       一键跑全部单测
releases/                历史版本 APK
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

## 版本历史

见 [CHANGELOG.md](CHANGELOG.md)。简要来说：

- **v1.4** —— 角色换成原始立绘（自动抠白底）并加上整体骨架动作（漂浮、蹦跳、摇摆、转圈等 10 种，说话配动作），应用图标一并换成鲸鱼娘；气泡宽度跟着字数走、长句自动折行；模式细分为仅 token 查询 / 仅桌宠 / 混合模式，混合模式会在余额变化时主动提醒
- **v1.3** —— 新增「token 查询模式」开关与桌宠模式（点角色随机说卖萌话、每分钟自动说一句），应用更名「鲸鱼娘桌宠」；气泡改为点击才弹出、7 秒后自动收起；查询余额时显示「正在刷新中…」
- **v1.2** —— 界面重做为卡片式（每个参数都有说明、带实时预览、高级设置默认折叠）；币种改为 15 种货币的下拉框，默认人民币；修复拖动后位置被重置的 bug
- **v1.1** —— 应用更名为「token 余额查询器」，内置角色贴图与桌面图标，扩展为六家服务商预设，金额路径与币种支持自动识别
- **v1.0** —— 首个可用版本，支持 DeepSeek 余额查询与悬浮显示

> 说明：v1.0 和 v1.1 只保留了签名后的 APK，源码快照没有留存，仓库中的 `app/src` 是 v1.4 的代码。

## 已知限制

- 在 Android 10（EMUI）上开发验证，Android 11+ 的悬浮窗行为差异没有实测过
- 部分国产 ROM 需要额外信任应用、手动开启「后台弹出界面」「自启动」权限，否则气泡可能在清理后台时被回收
- OpenAI 的余额接口对新版 Key 已不开放，可能返回 401
- 界面为中文，暂未做多语言
- 角色动画是持续重绘，虽然限了 30fps 且在窗口不可见时停止，但长时间挂着仍会比静态角色多耗一点电；可以在设置里关掉「角色动作」
- 角色是单张平面立绘，动作只能整只一起做，单独挥手臂或眨眼做不到（见上文「角色的动作」）
- 抠图用的是「与白底不连通的容差填充」，如果以后换成立绘背景不是纯白，或者角色内部有和背景同色又连通到边缘的区域，需要重新调参
- 没有单元测试之外的真机自动化测试，拖拽手感之类的交互细节需要实际使用才能确认

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
