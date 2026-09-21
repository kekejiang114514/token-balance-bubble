package com.coco.balancebubble;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * 设置界面。没有 aapt2，所有控件都在代码里搭，样式统一走 {@link Ui}。
 * 设计原则：常用项放最上面并配说明，危险/难懂的项收进「高级设置」。
 */
public class MainActivity extends Activity {

    /** 刷新间隔下拉框：只给几个好理解的档位，避免手填数字出错 */
    private static final String[] INT_LABEL = {
            "每 1 分钟", "每 5 分钟（推荐）", "每 10 分钟", "每 30 分钟",
            "每 1 小时", "每 3 小时", "每 6 小时", "每 12 小时"
    };
    private static final int[] INT_VALUE = {1, 5, 10, 30, 60, 180, 360, 720};

    private Ui ui;

    private LinearLayout body, advancedBox, statusBox, sizeBlock;
    private TextView advToggle, tvStatus, tvRaw, tvRawToggle, tvPreset, tvSizeVal;
    private TextView pillChar, pillCode, tvEye, pillToken;
    private Spinner spProvider, spCurrency, spInterval;
    private EditText etKey, etLabel, etBase, etPath, etExtract, etHeader, etPrefix, etCustom;
    private SeekBar sbSize;
    private TextView btnRun, btnStop, btnTest;
    private BubbleView preview;
    private ImageView previewChar;
    private Bitmap charBmp;

    private boolean filling = false;   // 程序化赋值期间忽略监听
    private boolean testing = false;
    private boolean showCharValue = true;
    private boolean showCodeValue = false;
    private boolean tokenEnabledValue = true;

    // ================= 生命周期 =================

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(14), ui.dp(16), ui.dp(14), ui.dp(36));
        sv.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        charBmp = loadChar();
        buildUi();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
        updatePreview();
    }

    // ================= 界面搭建 =================

    private void buildUi() {
        body.addView(hero());
        body.addView(guideCard());
        body.addView(providerCard());
        body.addView(keyCard());
        body.addView(displayCard());
        body.addView(actionCard());
        body.addView(advancedCard());
        body.addView(aboutCard());
    }

    /** 顶部横幅 */
    private View hero() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF2F6FED, 0xFF6B4BF0});
        g.setCornerRadius(ui.dp(18));
        l.setBackground(g);
        l.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = ui.dp(13);
        l.setLayoutParams(p);

        TextView t = ui.text("鲸鱼娘桌宠", 21f, 0xFFFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        l.addView(t);

        TextView s = ui.text("角色常驻桌面，点一下 = 说句卖萌话 / 刷新 API 余额", 12.5f, 0xFFD8E3FF);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = ui.dp(6);
        s.setLayoutParams(sp);
        l.addView(s);
        return l;
    }

    /** 三步上手 */
    private View guideCard() {
        LinearLayout c = ui.card();
        c.addView(ui.cardTitle("三步就能用"));
        c.addView(step("1", "选服务商", "下面第一个下拉框，选你充值的那家，比如 DeepSeek。"));
        c.addView(step("2", "粘贴 API Key", "去服务商官网的「API Keys」页面新建一个，复制粘贴到第二个框。"));
        c.addView(step("3", "点「保存并显示气泡」", "授权悬浮窗后角色就出现了。拖动可换位置，点一下按当前模式说话或查余额，长按回到本页。"));
        return c;
    }

    private View step(String no, String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = ui.dp(11);
        row.setLayoutParams(rp);

        TextView badge = ui.text(no, 12f, 0xFFFFFFFF);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.round(Ui.ACCENT, ui.dp(13), 0, 0));
        row.addView(badge, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = ui.dp(10);
        col.setLayoutParams(cp);
        TextView t = ui.text(title, 13.5f, Ui.TXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(t);
        col.addView(ui.hint(desc));
        row.addView(col);
        return row;
    }

    /** ① 服务商 */
    private View providerCard() {
        LinearLayout c = ui.card();
        c.addView(ui.cardTitle("① 选择服务商"));
        spProvider = ui.spinner(Presets.NAMES);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.topMargin = ui.dp(12);
        c.addView(spProvider, pp);
        spProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (filling) return;
                applyPreset(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        tvPreset = ui.text("", 11.6f, Ui.SUB);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = ui.dp(9);
        tvPreset.setLayoutParams(tp);
        c.addView(tvPreset);

        c.addView(ui.hint("选好服务商后，接口地址会自动填好，你不用管。下面只需要填 API Key。"));
        return c;
    }

    /** ② API Key + 测试 */
    private View keyCard() {
        LinearLayout c = ui.card();
        c.addView(ui.cardTitle("② 填 API Key"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = ui.dp(12);
        row.setLayoutParams(rp);

        etKey = ui.edit("sk-xxxxxxxxxxxxxxxx", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        row.addView(etKey, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvEye = ui.text("显示", 12.5f, Ui.ACCENT);
        tvEye.setPadding(ui.dp(12), ui.dp(10), ui.dp(4), ui.dp(10));
        tvEye.setClickable(true);
        tvEye.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleKeyMask();
            }
        });
        row.addView(tvEye);
        c.addView(row);

        c.addView(ui.hint("在你所选服务商的官网「API Keys / 密钥管理」页面创建后复制。"
                + "密钥只存在本机 App 私有目录，直接发往服务商，不经过任何第三方。"));

        btnTest = ui.button("测试连接（先确认能查到余额）", false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = ui.dp(13);
        btnTest.setLayoutParams(bp);
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doTest();
            }
        });
        c.addView(btnTest);

        statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbp.topMargin = ui.dp(11);
        statusBox.setLayoutParams(sbp);
        statusBox.setPadding(ui.dp(12), ui.dp(11), ui.dp(12), ui.dp(11));

        tvStatus = ui.text("", 12.6f, Ui.TXT);
        statusBox.addView(tvStatus);

        tvRawToggle = ui.link("查看接口原始返回 ▾");
        tvRawToggle.setVisibility(View.GONE);
        statusBox.addView(tvRawToggle);

        tvRaw = ui.text("", 10.8f, Ui.SUB);
        tvRaw.setTypeface(Typeface.MONOSPACE);
        tvRaw.setVisibility(View.GONE);
        statusBox.addView(tvRaw);

        tvRawToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = tvRaw.getVisibility() != View.VISIBLE;
                tvRaw.setVisibility(show ? View.VISIBLE : View.GONE);
                tvRawToggle.setText(show ? "收起接口原始返回 ▴" : "查看接口原始返回 ▾");
            }
        });
        c.addView(statusBox);
        return c;
    }

    /** ③ 显示与刷新 */
    private View displayCard() {
        LinearLayout c = ui.card();
        c.addView(ui.cardTitle("③ 模式与显示"));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mrp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mrp.topMargin = ui.dp(12);
        modeRow.setLayoutParams(mrp);
        modeRow.addView(ui.text("token 查询模式", 13.2f, Ui.TXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pillToken = pill();
        pillToken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tokenEnabledValue = !tokenEnabledValue;
                renderToggles();
                applyModeChange();
            }
        });
        modeRow.addView(pillToken);
        c.addView(modeRow);
        c.addView(ui.hint("开着＝点角色查余额，气泡里会先显示「正在刷新中…」。"
                + "关掉＝只当桌宠，点角色随机说句卖萌话，而且每分钟自动冒一句。"));

        c.addView(ui.divider());

        c.addView(ui.label("金额币种"));
        String[] items = new String[Currencies.CODES.length];
        for (int i = 0; i < items.length; i++) items[i] = Currencies.label(i);
        spCurrency = ui.spinner(items);
        c.addView(spCurrency);
        spCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (filling) return;
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        c.addView(ui.hint("接口只返回一个数字，币种是显示用的。默认人民币，"
                + "用美元计费的服务（OpenRouter / OpenAI）记得改成美元。"));

        c.addView(ui.label("自动刷新间隔"));
        spInterval = ui.spinner(INT_LABEL);
        c.addView(spInterval);
        spInterval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (filling) return;
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        c.addView(ui.hint("间隔越短越及时，但接口调用次数更多。查询余额一般很宽松，5 分钟够用。"));

        c.addView(ui.divider());

        LinearLayout charRow = new LinearLayout(this);
        charRow.setOrientation(LinearLayout.HORIZONTAL);
        charRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crp.topMargin = ui.dp(12);
        charRow.setLayoutParams(crp);
        TextView cl = ui.text("气泡下方显示角色", 13.2f, Ui.TXT);
        charRow.addView(cl, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pillChar = pill();
        pillChar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCharValue = !showCharValue;
                renderToggles();
                updatePreview();
            }
        });
        charRow.addView(pillChar);
        c.addView(charRow);
        c.addView(ui.hint("关掉就只留一个气泡，适合不想让角色挡视线的时候。"));

        sizeBlock = new LinearLayout(this);
        sizeBlock.setOrientation(LinearLayout.VERTICAL);
        c.addView(sizeBlock);

        LinearLayout sizeHead = new LinearLayout(this);
        sizeHead.setOrientation(LinearLayout.HORIZONTAL);
        sizeHead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shp.topMargin = ui.dp(13);
        sizeHead.setLayoutParams(shp);
        sizeHead.addView(ui.text("角色大小", 13.2f, Ui.TXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvSizeVal = ui.text("96 dp", 12.5f, Ui.SUB);
        sizeHead.addView(tvSizeVal);
        sizeBlock.addView(sizeHead);

        sbSize = new SeekBar(this);
        sbSize.setMax(100);
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tvSizeVal.setText(sizeDp() + " dp");
                if (fromUser) updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        sizeBlock.addView(sbSize);

        c.addView(ui.divider());

        TextView pvTitle = ui.text("效果预览", 12.5f, Ui.SUB);
        LinearLayout.LayoutParams ptp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ptp.topMargin = ui.dp(12);
        pvTitle.setLayoutParams(ptp);
        c.addView(pvTitle);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackground(Ui.round(0xFFE7EDF7, ui.dp(13), ui.dp(1), Ui.LINE));
        panel.setPadding(ui.dp(12), ui.dp(18), ui.dp(12), ui.dp(14));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = ui.dp(8);
        panel.setLayoutParams(plp);

        preview = new BubbleView(this);
        panel.addView(preview);
        previewChar = new ImageView(this);
        panel.addView(previewChar);
        c.addView(panel);
        return c;
    }

    /** 主操作按钮 */
    private View actionCard() {
        LinearLayout c = ui.card();
        c.addView(ui.cardTitle("④ 开启气泡"));

        btnRun = ui.button("保存并显示气泡", true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = ui.dp(12);
        btnRun.setLayoutParams(bp);
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start(true);
            }
        });
        c.addView(btnRun);

        btnStop = ui.button("关闭气泡", false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = ui.dp(9);
        btnStop.setLayoutParams(sp);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, BubbleService.class));
                Prefs.setRunning(MainActivity.this, false);
                setStatus("气泡已关闭。重新点上面的按钮可以再打开。", 0);
                updateUi();
            }
        });
        c.addView(btnStop);

        c.addView(ui.hint("开启后回到桌面就能看到角色：拖动＝换位置（松开后记住），"
                + "点一下＝按当前模式说话或刷新余额，长按＝回到这个设置页。"));
        return c;
    }

    /** 高级设置，默认折叠 */
    private View advancedCard() {
        LinearLayout c = ui.card();

        advToggle = ui.text("▸  高级设置（不确定就别改）", 13.5f, Ui.ACCENT);
        advToggle.setTypeface(Typeface.DEFAULT_BOLD);
        advToggle.setPadding(0, ui.dp(3), 0, ui.dp(3));
        advToggle.setClickable(true);
        advToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = advancedBox.getVisibility() != View.VISIBLE;
                advancedBox.setVisibility(show ? View.VISIBLE : View.GONE);
                advToggle.setText(show ? "▾  高级设置（不确定就别改）"
                        : "▸  高级设置（不确定就别改）");
            }
        });
        c.addView(advToggle);

        advancedBox = new LinearLayout(this);
        advancedBox.setOrientation(LinearLayout.VERTICAL);
        advancedBox.setVisibility(View.GONE);

        advancedBox.addView(ui.hint("下面这些是接口细节。选完服务商之后已经自动填好，"
                + "只有换到不在列表里的服务、或者报错时才会用到。"));

        etLabel = advField("气泡上显示的名字（留空则只显示金额）", "",
                InputType.TYPE_CLASS_TEXT, "例如 DeepSeek。气泡第一行显示的就是它。");

        etBase = advField("Base URL（接口域名）", "https://api.deepseek.com",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                "服务商接口的根地址，以 https:// 开头，结尾不要带 /。");

        etPath = advField("余额接口路径", "/user/balance",
                InputType.TYPE_CLASS_TEXT,
                "查询余额的接口，例如 /user/balance。报 404 多半是这里填错了。");

        etExtract = advField("金额字段路径（留空＝自动识别）", "balance_infos.0.total_balance",
                InputType.TYPE_CLASS_TEXT,
                "余额在返回结果里的位置，用点号表示层级，数组写序号，"
                + "例如 balance_infos.0.total_balance。留空则自动找第一个像余额的数字。");

        etHeader = advField("认证请求头名称", "Authorization",
                InputType.TYPE_CLASS_TEXT,
                "绝大多数服务商都是 Authorization。个别服务商（如 OpenRouter）还需额外的头，"
                + "本 App 只支持改这一个。");

        etPrefix = advField("认证前缀", "Bearer ",
                InputType.TYPE_CLASS_TEXT, "拼接方式：前缀 + 你的 Key。默认 Bearer 加一个空格，不要删空格。");

        etCustom = advField("自定义货币符号", "",
                InputType.TYPE_CLASS_TEXT,
                "只有当上面的「金额币种」选了「自定义符号」时才生效，例如填 元 或 USDT。");

        LinearLayout codeRow = new LinearLayout(this);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        codeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams crp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crp.topMargin = ui.dp(16);
        codeRow.setLayoutParams(crp);
        codeRow.addView(ui.text("金额后面补上币种代码", 13.2f, Ui.TXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pillCode = pill();
        pillCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCodeValue = !showCodeValue;
                renderToggles();
                updatePreview();
            }
        });
        codeRow.addView(pillCode);
        advancedBox.addView(codeRow);
        advancedBox.addView(ui.hint("开启后气泡显示成 33.83 CNY，方便区分人民币和日元这类同符号的币种。"));

        TextView btnReset = ui.button("把气泡放回默认位置", false);
        LinearLayout.LayoutParams rp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp1.topMargin = ui.dp(16);
        btnReset.setLayoutParams(rp1);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Prefs.running(MainActivity.this)) {
                    setStatus("气泡还没开，先点上面的「保存并显示气泡」。", 0);
                    return;
                }
                Intent i = new Intent(MainActivity.this, BubbleService.class);
                i.setAction(BubbleService.ACTION_RESET_POS);
                startService(i);
                setStatus("气泡已回到默认位置（屏幕下方中间），再拖一次即可。", 1);
            }
        });
        advancedBox.addView(btnReset);

        TextView btnReload = ui.button("恢复当前服务商的默认参数", false);
        LinearLayout.LayoutParams rp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp2.topMargin = ui.dp(9);
        btnReload.setLayoutParams(rp2);
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyPreset(spProvider.getSelectedItemPosition());
                save();
                setStatus("已按当前服务商重填接口参数。", 1);
            }
        });
        advancedBox.addView(btnReload);

        c.addView(advancedBox);
        return c;
    }

    private View aboutCard() {
        LinearLayout c = ui.card();
        c.addView(ui.cardTitle("说明"));
        c.addView(ui.hint("· 气泡平时是收起的，点一下角色才弹出来，几秒后自动收起。"));
        c.addView(ui.hint("· 密钥只保存在本机 /data/data/com.coco.balancebubble/ 里，"
                + "查询请求直接发给你填的服务商地址，中间不经过任何服务器。"));
        c.addView(ui.hint("· 开机自启只在「上次开着气泡 + 已授权悬浮窗 + 已填密钥」都满足时才会恢复。"));
        c.addView(ui.hint("· 版本 1.3　包名 com.coco.balancebubble"));
        return c;
    }

    // ================= 小控件 =================

    private EditText advField(String label, String hintText, int inputType, String desc) {
        advancedBox.addView(ui.label(label));
        EditText e = ui.edit(hintText, inputType);
        advancedBox.addView(e);
        advancedBox.addView(ui.hint(desc));
        if (label.startsWith("气泡上显示")) {
            e.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    if (!filling) updatePreview();
                }
            });
        }
        return e;
    }

    private TextView pill() {
        TextView t = new TextView(this);
        t.setTextSize(13f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(ui.dp(15), ui.dp(7), ui.dp(15), ui.dp(7));
        t.setClickable(true);
        return t;
    }

    private void renderToggles() {
        if (pillChar != null) {
            pillChar.setText(showCharValue ? "已开启" : "已关闭");
            pillChar.setTextColor(showCharValue ? Ui.OK : Ui.SUB);
            pillChar.setBackground(Ui.round(showCharValue ? Ui.OK_BG : 0xFFF1F4F9,
                    ui.dp(20), ui.dp(1), showCharValue ? 0xFFB6E2CC : Ui.LINE));
        }
        if (pillCode != null) {
            pillCode.setText(showCodeValue ? "已开启" : "已关闭");
            pillCode.setTextColor(showCodeValue ? Ui.OK : Ui.SUB);
            pillCode.setBackground(Ui.round(showCodeValue ? Ui.OK_BG : 0xFFF1F4F9,
                    ui.dp(20), ui.dp(1), showCodeValue ? 0xFFB6E2CC : Ui.LINE));
        }
        if (pillToken != null) {
            pillToken.setText(tokenEnabledValue ? "已开启" : "已关闭");
            pillToken.setTextColor(tokenEnabledValue ? Ui.OK : Ui.SUB);
            pillToken.setBackground(Ui.round(tokenEnabledValue ? Ui.OK_BG : 0xFFF1F4F9,
                    ui.dp(20), ui.dp(1), tokenEnabledValue ? 0xFFB6E2CC : Ui.LINE));
        }
        if (sizeBlock != null) {
            sizeBlock.setVisibility(showCharValue ? View.VISIBLE : View.GONE);
        }
    }

    /** 模式改了立刻生效：服务在跑就让它重排定时任务。 */
    private void applyModeChange() {
        Prefs.setTokenEnabled(this, tokenEnabledValue);
        if (!Prefs.running(this)) return;
        Intent i = new Intent(this, BubbleService.class);
        i.setAction(BubbleService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void setStatus(String text, int kind) {
        if (statusBox == null) return;
        statusBox.setVisibility(View.VISIBLE);
        tvStatus.setText(text);
        int bg, fg, stroke;
        if (kind == 1) {
            bg = Ui.OK_BG;
            fg = Ui.OK;
            stroke = 0xFFB6E2CC;
        } else if (kind == 2) {
            bg = Ui.ERR_BG;
            fg = Ui.ERR;
            stroke = 0xFFF3C9C3;
        } else {
            bg = Ui.INFO_BG;
            fg = Ui.TXT;
            stroke = 0xFFCBDCFB;
        }
        statusBox.setBackground(Ui.round(bg, ui.dp(11), ui.dp(1), stroke));
        tvStatus.setTextColor(fg);
    }

    private void toggleKeyMask() {
        boolean masked = (etKey.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
        etKey.setInputType(InputType.TYPE_CLASS_TEXT
                | (masked ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        tvEye.setText(masked ? "隐藏" : "显示");
        if (etKey.getText() != null) etKey.setSelection(etKey.getText().length());
    }

    // ================= 预览 =================

    private void updatePreview() {
        if (preview == null || spCurrency == null) return;
        int ci = spCurrency.getSelectedItemPosition();
        String code = Currencies.code(ci);
        String custom = etCustom == null ? "" : etCustom.getText().toString().trim();
        String sym = Currencies.prefix(code, custom, "CNY", showCodeValue);
        String name = etLabel == null ? "" : etLabel.getText().toString().trim();
        String label = name.isEmpty() ? "" : name + " 余额";
        preview.setData(label, sym + "33.83" + Currencies.suffix(code, showCodeValue), false);

        if (previewChar != null) {
            if (charBmp != null) {
                int w = ui.dp(sizeDp());
                int h = Math.max(1, w * charBmp.getHeight() / charBmp.getWidth());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
                lp.topMargin = -ui.dp(10);
                previewChar.setLayoutParams(lp);
                previewChar.setImageBitmap(charBmp);
            }
            previewChar.setVisibility(showCharValue ? View.VISIBLE : View.GONE);
        }
    }

    private Bitmap loadChar() {
        try {
            InputStream is = getAssets().open("char.png");
            Bitmap b = BitmapFactory.decodeStream(is);
            is.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    // ================= 数据 =================

    private void applyPreset(int pos) {
        if (pos < 0 || pos >= Presets.NAMES.length) return;
        if (Presets.isCustom(pos)) {
            // 自定义：保留已经填好的内容，避免误清空
            if (etLabel.getText().toString().trim().isEmpty()) etLabel.setText(Presets.label(pos));
            refreshPresetInfo();
            updatePreview();
            return;
        }
        etLabel.setText(Presets.label(pos));
        etBase.setText(Presets.base(pos));
        etPath.setText(Presets.path(pos));
        etExtract.setText(Presets.extract(pos));
        etHeader.setText("Authorization");
        etPrefix.setText("Bearer ");
        spCurrency.setSelection(Currencies.indexOfCode(Presets.currency(pos)));
        refreshPresetInfo();
        updatePreview();
    }

    private void refreshPresetInfo() {
        if (tvPreset == null) return;
        int pos = spProvider == null ? 0 : spProvider.getSelectedItemPosition();
        String base = etBase == null ? "" : etBase.getText().toString().trim();
        String path = etPath == null ? "" : etPath.getText().toString().trim();
        String url = BalanceApi.join(base, path);
        tvPreset.setText("接口：" + (url.isEmpty() ? "（还没填）" : url)
                + "\n" + Presets.note(pos));
    }

    private void load() {
        filling = true;
        Prefs.Draft d = Prefs.load(this);
        int pi = Presets.indexOfName(d.provider);
        spProvider.setSelection(pi);
        if (d.base == null || d.base.trim().isEmpty()) applyPreset(pi);
        etLabel.setText(d.label);
        etBase.setText(d.base);
        etPath.setText(d.path);
        etKey.setText(d.key == null ? "" : d.key);
        etExtract.setText(d.extract);
        etHeader.setText(d.header);
        etPrefix.setText(d.prefix);
        etCustom.setText(d.curCustom);
        spCurrency.setSelection(Currencies.indexOfCode(d.curCode));
        spInterval.setSelection(intervalIndex(d.interval));
        showCharValue = d.showChar;
        showCodeValue = d.showCode;
        tokenEnabledValue = d.tokenEnabled;
        sbSize.setProgress(sizeProgress(d.charSize));
        tvSizeVal.setText(d.charSize + " dp");
        filling = false;
        renderToggles();
        refreshPresetInfo();
        updatePreview();
        updateUi();
    }

    private Prefs.Draft collect() {
        Prefs.Draft d = new Prefs.Draft();
        d.provider = Presets.NAMES[Math.max(0, spProvider.getSelectedItemPosition())];
        d.label = etLabel.getText().toString().trim();
        d.base = etBase.getText().toString().trim();
        d.path = etPath.getText().toString().trim();
        d.key = etKey.getText().toString().trim();
        d.extract = etExtract.getText().toString().trim();
        d.header = etHeader.getText().toString().trim();
        d.prefix = etPrefix.getText().toString();
        d.curCode = Currencies.code(spCurrency.getSelectedItemPosition());
        d.curCustom = etCustom.getText().toString().trim();
        d.interval = INT_VALUE[intervalIndex(spInterval.getSelectedItemPosition())];
        d.charSize = sizeDp();
        d.showChar = showCharValue;
        d.showCode = showCodeValue;
        d.tokenEnabled = tokenEnabledValue;
        return d;
    }

    private void save() {
        Prefs.save(this, collect());
    }

    private int intervalIndex(int minutes) {
        int best = 1;
        int diff = Integer.MAX_VALUE;
        for (int i = 0; i < INT_VALUE.length; i++) {
            int v = Math.abs(INT_VALUE[i] - minutes);
            if (v < diff) {
                diff = v;
                best = i;
            }
        }
        return best;
    }

    private int sizeDp() {
        return 60 + (int) Math.round(sbSize.getProgress() * 1.6);
    }

    private int sizeProgress(int dpValue) {
        int p = (int) Math.round((dpValue - 60) / 1.6);
        return Math.max(0, Math.min(100, p));
    }

    // ================= 动作 =================

    private void start(boolean checkPermission) {
        Prefs.Draft d = collect();
        if (d.key.isEmpty()) {
            setStatus("还没填 API Key。去服务商官网的「API Keys」页面创建一个，粘贴到第 ② 步的框里。", 2);
            return;
        }
        if (d.base.isEmpty()) {
            setStatus("Base URL 是空的。回到第 ① 步重新选一次服务商，或在高级设置里手动填。", 2);
            return;
        }
        Prefs.save(this, d);
        if (checkPermission && !hasOverlay()) {
            askOverlay();
            return;
        }
        Intent i = new Intent(this, BubbleService.class);
        i.setAction(BubbleService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Prefs.setRunning(this, true);
        setStatus("已开启。回到桌面即可看到角色，拖动换位置 / 点一下按模式说话或查余额 / 长按回到本页。", 1);
        updateUi();
    }

    private void doTest() {
        if (testing) return;
        final Prefs.Draft d = collect();
        testing = true;
        btnTest.setEnabled(false);
        setStatus("正在请求 " + BalanceApi.join(d.base, d.path) + " …\n请稍等，最长 20 秒。", 0);
        tvRaw.setVisibility(View.GONE);
        tvRawToggle.setVisibility(View.GONE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BalanceApi.Result r = BalanceApi.query(MainActivity.this, d.base, d.path,
                        d.key, d.header, d.prefix, d.extract, d.curCode, d.curCustom,
                        d.showCode, d.label);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        testing = false;
                        btnTest.setEnabled(true);
                        showResult(r, d);
                    }
                });
            }
        }).start();
    }

    private void showResult(BalanceApi.Result r, Prefs.Draft d) {
        StringBuilder sb = new StringBuilder();
        sb.append("GET ").append(BalanceApi.join(d.base, d.path)).append('\n');
        String hn = d.header == null || d.header.isEmpty() ? "Authorization" : d.header;
        sb.append("请求头 ").append(hn).append(": ");
        sb.append(d.key.isEmpty() ? "(未填)" : mask(d.key)).append('\n');
        sb.append("取值字段 ").append(r.usedPath == null ? "-" : r.usedPath).append("\n\n");
        String raw = r.raw == null ? "" : r.raw.trim();
        if (raw.length() > 1500) raw = raw.substring(0, 1500) + "\n…（已截断）";
        sb.append(raw.isEmpty() ? "(没有返回内容)" : raw);
        tvRaw.setText(sb.toString());
        tvRawToggle.setVisibility(View.VISIBLE);
        tvRawToggle.setText("查看接口原始返回 ▾");

        if (r.ok) {
            String extra = r.available ? "" : "\n注意：接口显示该账户当前不可用。";
            setStatus("测试成功\n当前余额：" + r.currency + r.amount
                    + "\n取值字段：" + r.usedPath + extra, 1);
        } else {
            setStatus("测试没通过\n" + r.error, 2);
        }
    }

    private String mask(String k) {
        if (k.length() <= 10) return "****";
        return k.substring(0, 6) + "…" + k.substring(k.length() - 4);
    }

    private void updateUi() {
        boolean run = Prefs.running(this);
        if (btnRun != null) btnRun.setText(run ? "保存并刷新气泡" : "保存并显示气泡");
        if (btnStop != null) btnStop.setText(run ? "关闭气泡（当前运行中）" : "关闭气泡");
        if (!run && statusBox != null && statusBox.getVisibility() != View.VISIBLE) {
            String last = Prefs.lastAmount(this);
            String err = Prefs.lastInfo(this);
            if (err != null && err.length() > 0) {
                setStatus("上次查询失败：\n" + err, 2);
            } else if (last != null && last.length() > 0 && !"--".equals(last)) {
                setStatus("上次查询结果 " + last, 0);
            }
        }
    }

    // ================= 权限 =================

    private boolean hasOverlay() {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (Settings.canDrawOverlays(this)) return true;
        android.app.AppOpsManager am = (android.app.AppOpsManager)
                getSystemService(Context.APP_OPS_SERVICE);
        try {
            Integer mode = (Integer) am.getClass()
                    .getMethod("checkOpNoThrow", int.class, int.class, String.class)
                    .invoke(am, 24, android.os.Process.myUid(), getPackageName());
            return mode != null && mode.intValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void askOverlay() {
        toast("请在系统页面里允许「显示在其他应用上层」，回来后再点一次");
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception e2) {
                toast("请到系统设置 → 应用 → 特殊权限里授予悬浮窗权限");
            }
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
