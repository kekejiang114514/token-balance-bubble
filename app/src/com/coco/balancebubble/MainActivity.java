package com.coco.balancebubble;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

/**
 * 设置页。
 *
 * <p>整页是「竖着的四张标签」：连接 / 气泡 / 角色 / 关于。顶部常驻一条实时预览，
 * 里面就是悬浮窗真正用的那两个视图（气泡 + 角色），改任何一项都能立刻看见效果。
 * 所有改动都写进 {@link Prefs}，然后给正在跑的悬浮窗发一条 START，
 * 服务端会重建气泡样式并原地刷新列布局，不用重启服务。
 */
public class MainActivity extends Activity {

    private Ui ui;
    private Theme theme;
    private Prefs.Draft cfg;

    private ScrollView scroller;
    private LinearLayout page;
    private FrameLayout previewBox;
    private BubbleView previewBubble;
    private PetView previewPet;
    private EditText sampleInput;
    private TextView heroBadge;
    private TextView heroSub;
    private TextView tipLine;
    private Ui.Segment tabs;
    private int tab = 0;
    /** 重建界面期间为真，避免控件初始化时触发保存/推送 */
    private boolean building = false;
    /** 预览里用的示例文字（气泡 tab 里可以直接改，用来试断开行效果） */
    private String sample = "¥33.83";

    /** 服务商下拉：换服务商要顺带改接口地址和币种，所以留个引用 */
    private Spinner spPreset;
    /** 币种下拉 */
    private Spinner spCurrency;
    /** 密钥输入框和它的「显示 / 隐藏」按钮 */
    private EditText etKey;
    private TextView btnShowKey;
    private boolean keyShown = false;
    /** 「立即测试」的结果文字 */
    private TextView tvTest;

    /** 存盘是同步的，但往悬浮窗推 START 会重建气泡，打字时没必要每敲一个字推一次 */
    private final Handler pusher = new Handler(Looper.getMainLooper());
    private final Runnable push = new Runnable() {
        @Override
        public void run() {
            if (Prefs.running(MainActivity.this)) send(BubbleService.ACTION_START);
        }
    };

    /** 输入框取值回调：不同字段存的东西不一样，搬进 cfg 的动作交给调用方。 */
    private interface Field {
        void set(String s);
    }

    /** 滑杆取值回调。 */
    private interface IntSetter {
        void set(int v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = Prefs.load(this);
        theme = new Theme(cfg.theme, cfg.darkNow());
        ui = new Ui(this, theme);
        scroller = new ScrollView(this);
        scroller.setBackgroundColor(theme.bg());
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, ui.dp(28));
        scroller.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroller);
        buildAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!building) buildAll();
    }

    /** 整页重画：换主题、切标签、复位设置都走这里。 */
    private void buildAll() {
        building = true;
        page.removeAllViews();
        addHero();
        addPreview();
        addTabs();
        addTabBody();
        building = false;
        refreshStatus();
    }
    // ==================== 顶部 ====================

    /** 顶部横幅：名字、状态、启动/停止、立即刷新。 */
    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                theme.heroColors());
        g.setCornerRadii(new float[]{0, 0, 0, 0, ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20)});
        hero.setBackground(g);
        hero.setPadding(ui.dp(18), ui.dp(22), ui.dp(18), ui.dp(16));

        TextView title = new TextView(this);
        title.setText("鲸鱼娘桌宠");
        title.setTextSize(21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        hero.addView(title);

        heroSub = new TextView(this);
        heroSub.setTextSize(12f);
        heroSub.setTextColor(0x99FFFFFF);
        LinearLayout.LayoutParams sp = ui.wrap();
        sp.topMargin = ui.dp(4);
        heroSub.setLayoutParams(sp);
        hero.addView(heroSub);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bp = ui.wrap();
        bp.topMargin = ui.dp(14);
        btns.setLayoutParams(bp);

        heroBadge = ui.badge("已停止", false);
        btns.addView(heroBadge);

        TextView refresh = heroButton("立即刷新");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(BubbleService.ACTION_REFRESH);
                toast("已让悬浮窗刷新余额");
            }
        });
        LinearLayout.LayoutParams rp = ui.wrap();
        rp.leftMargin = ui.dp(8);
        btns.addView(refresh, rp);

        final TextView power = heroButton(Prefs.running(this) ? "停止桌宠" : "启动桌宠");
        power.setId(android.R.id.button1);
        power.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Prefs.running(MainActivity.this)) {
                    send(BubbleService.ACTION_STOP);
                    Prefs.setRunning(MainActivity.this, false);
                } else {
                    send(BubbleService.ACTION_START);
                    Prefs.setRunning(MainActivity.this, true);
                }
                buildAll();
            }
        });
        LinearLayout.LayoutParams pp = ui.wrap();
        pp.leftMargin = ui.dp(8);
        btns.addView(power, pp);

        hero.addView(btns);
        page.addView(hero);
    }

    /** 顶部横幅上的小按钮：半透明白底 + 白字。 */
    private TextView heroButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(12.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(0xFF1D2333);
        b.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xE6FFFFFF);
        g.setCornerRadius(ui.dp(14));
        b.setBackground(g);
        return b;
    }

    // ==================== 实时预览 ====================

    /**
     * 预览面板：里面就是悬浮窗用的同一套视图（{@link BubbleView} + {@link PetView}），
     * 只是尺寸按屏幕缩放了一下。改字号/宽度/行数/颜色/尖角方向都能立刻看到效果。
     */
    private void addPreview() {
        LinearLayout card = ui.card();
        card.addView(ui.cardTitle("实时预览", "下面这个气泡和悬浮窗里的完全是同一套代码，改设置它会立刻变"));

        sampleInput = ui.edit(sample, 1);
        sampleInput.setHint("输入一段文字试试折行效果");
        LinearLayout.LayoutParams ip = ui.wrap();
        ip.topMargin = ui.dp(8);
        sampleInput.setLayoutParams(ip);
        sampleInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                sample = e.toString();
                fillPreview();
            }
        });
        card.addView(sampleInput);

        previewBox = new FrameLayout(this);
        int h = ui.dp((int) (Prefs.charSize(this) * PetView.VIEW_H_RATIO) + 130);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
        fp.topMargin = ui.dp(10);
        previewBox.setLayoutParams(fp);
        previewBox.setBackground(ui.round(theme.panel(), ui.dp(14), ui.dp(1), theme.line()));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        previewBubble = new BubbleView(this, BubbleStyle.build(cfg, ui.density()));
        previewPet = new PetView(this);
        previewPet.setAnimated(cfg.animate);
        previewPet.setIdleLevel(cfg.petIdle);
        col.addView(previewBubble);
        col.addView(previewPet, new LinearLayout.LayoutParams(
                ui.dp(cfg.charSize), ui.dp((int) (cfg.charSize * PetView.VIEW_H_RATIO))));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        previewBox.addView(col, cp);
        card.addView(previewBox);

        tipLine = ui.text("", 11.5f, theme.sub());
        LinearLayout.LayoutParams tp = ui.wrap();
        tp.topMargin = ui.dp(8);
        tipLine.setLayoutParams(tp);
        card.addView(tipLine);

        page.addView(card);
        fillPreview();
    }

    /** 把当前设置 + 示例文字灌进预览里的气泡。 */
    private void fillPreview() {
        if (previewBubble == null) return;
        previewBubble.applyStyle(BubbleStyle.build(cfg, ui.density()));
        String label = cfg.showTitle && !cfg.label.trim().isEmpty()
                ? cfg.label.trim() + " 余额" : "";
        previewBubble.setData(label, sample.isEmpty() ? " " : sample, false);
        previewBubble.setVisibility(View.VISIBLE);
        if (tipLine != null) tipLine.setText(previewBubble.describe());
    }
    // ==================== 标签页 ====================

    private static final String[] TAB_NAMES = {"连接", "气泡", "角色", "关于"};

    private void addTabs() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.leftMargin = ui.dp(14);
        bp.rightMargin = ui.dp(14);
        bp.topMargin = ui.dp(12);
        box.setLayoutParams(bp);

        tabs = ui.segment(TAB_NAMES, tab, new Ui.OnPick() {
            @Override
            public void onPick(int i) {
                tab = i;
                buildAll();
                scroller.post(new Runnable() {
                    @Override
                    public void run() {
                        scroller.smoothScrollTo(0, 0);
                    }
                });
            }
        });
        box.addView(tabs.row);
        page.addView(box);
    }

    /** 当前标签页的内容（每个 tab 自己往 page 里加卡片）。 */
    private void addTabBody() {
        switch (tab) {
            case 0:
                tabConn();
                break;
            case 1:
                tabBubble();
                break;
            case 2:
                tabPet();
                break;
            default:
                tabAbout();
                break;
        }
    }

    // ==================== 公共小工具 ====================

    /** 给悬浮窗发指令。 */
    private void send(String action) {
        Intent i = new Intent(this, BubbleService.class);
        i.setAction(action);
        startService(i);
    }

    /**
     * 存盘 + 通知悬浮窗换外观（重建界面之前先调用它）。
     *
     * <p>存盘是同步的，往服务推 START 会重建气泡窗口；设置页里打字、拖滑杆时
     * 一次输入可能触发十几个字符，所以推 START 合并到 180ms 之后再发一次，
     * 免得气泡跟着抖。存盘不受影响，退出页面也不会丢。
     */
    private void apply() {
        cfg.save(this);
        pusher.removeCallbacks(push);
        pusher.postDelayed(push, 180);
    }

    /** 存盘 + 刷新预览（改气泡外观的控件都走这条）。 */
    private void applyLive() {
        apply();
        fillPreview();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** 顶部状态文字。 */
    private void refreshStatus() {
        boolean on = Prefs.running(this);
        if (heroBadge != null) {
            heroBadge.setText(on ? "运行中" : "已停止");
            heroBadge.setTextColor(on ? theme.ok() : theme.sub());
            heroBadge.setBackground(ui.pill(on ? theme.okBg() : theme.field()));
        }
        if (heroSub != null) {
            String mode = Prefs.MODE_NAMES[cfg.mode];
            String key = cfg.key.trim().isEmpty() ? "还没填 API Key" : "已配置";
            heroSub.setText(mode + " · " + key + " · 气泡 "
                    + cfg.bubbleMaxW + "dp / " + cfg.bubbleFont + "sp / " + cfg.bubbleLines + " 行");
        }
        if (page != null && page.getChildCount() > 0) {
            View hero = page.getChildAt(0);
            if (hero instanceof LinearLayout) {
                View b = ((LinearLayout) hero).findViewById(android.R.id.button1);
                if (b instanceof TextView) ((TextView) b).setText(on ? "停止桌宠" : "启动桌宠");
            }
        }
    }
    /**
     * 换主题 / 换深浅模式之后整页重画。
     *
     * <p>颜色是在建控件的时候一次写死的（这个项目没有 XML 主题），
     * 所以只能把整棵树重新搭一遍，见 {@link #buildAll()}。
     */
    private void rebuild() {
        theme = new Theme(cfg.theme, cfg.darkNow());
        ui = new Ui(this, theme);
        scroller.setBackgroundColor(theme.bg());
        buildAll();
    }

    /** 一行输入框：字段名 + 输入框，边打字边存。 */
    private EditText addField(LinearLayout into, String label, String value, final Field f) {
        into.addView(ui.label(label));
        final EditText e = ui.edit(value, android.text.InputType.TYPE_CLASS_TEXT);
        e.setText(value);
        e.setSelection(e.getText().length());
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable ed) {
                if (building) return;
                f.set(ed.toString());
            }
        });
        into.addView(e);
        return e;
    }

    /** 一行滑杆：标题 + 说明 + 当前值，拖动的时候预览跟着变。 */
    private void addSlider(LinearLayout into, String title, String desc, final int min, int max,
                           int value, Ui.Fmt fmt, final IntSetter set) {
        Ui.Slider s = ui.sliderRow(title, desc, min, max, value, fmt,
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        set.set(Ui.sliderValue(bar, min));
                        applyLive();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                    }
                });
        into.addView(s.row);
    }

    /**
     * 拿现在填的这一套设置真去请求一次，结果显示在按钮下面。
     *
     * <p>不走悬浮窗（悬浮窗可能还没启动），直接在设置页里跑一遍，
     * 边调边看是哪一步不对。请求放在后台线程，结果回主线程显示。
     */
    private void testNow() {
        if (tvTest == null) return;
        apply();
        if (cfg.key.trim().isEmpty()) {
            tvTest.setTextColor(theme.sub());
            tvTest.setText("先在上面填 API Key，没有它连不上。");
            return;
        }
        tvTest.setTextColor(theme.sub());
        tvTest.setText("正在请求 " + BalanceApi.join(cfg.base, cfg.path) + " …");
        final Prefs.Draft snap = cfg;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BalanceApi.Result r = BalanceApi.query(MainActivity.this, snap.base, snap.path,
                        snap.key, snap.header, snap.prefix, snap.extract, snap.curCode,
                        snap.curCustom, snap.showCode, snap.label);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (tvTest == null) return;
                        if (!r.ok) {
                            tvTest.setTextColor(theme.err());
                            tvTest.setText("没成功：" + r.error);
                            return;
                        }
                        String sym = r.currency == null ? "" : r.currency;
                        tvTest.setTextColor(r.error.isEmpty() ? theme.ok() : theme.sub());
                        tvTest.setText("成功：读到 " + sym + r.amount
                                + "（取值 " + r.usedPath + "）"
                                + (r.error.isEmpty() ? "" : "｜注意：" + r.error));
                    }
                });
            }
        }).start();
    }

    /**
     * 把当前设置导成一段文本放进剪贴板（换手机、留个底用）。
     *
     * <p>导出的是存盘里的内容而不是界面上的控件的值，所以导入回来能完整还原。
     * 文本里有 API Key，提示里说明一下。
     */
    private void copyConfig() {
        try {
            JSONObject box = new JSONObject();
            box.put("app", "balance-bubble");
            box.put("version", versionName());
            JSONObject prefs = new JSONObject();
            for (Map.Entry<String, ?> e : Prefs.get(this).getAll().entrySet()) {
                if (isVolatile(e.getKey())) continue;
                prefs.put(e.getKey(), e.getValue());
            }
            box.put("prefs", prefs);
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("balance-bubble", box.toString(2)));
            toast("配置已复制到剪贴板。里面有 API Key，别外传。");
        } catch (Exception e) {
            toast("导出失败：" + e);
        }
    }

    /** 从剪贴板读回一份 {@link #copyConfig()} 导出的文本，覆盖当前设置。 */
    private void importConfig() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                toast("剪贴板是空的");
                return;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            JSONObject box = new JSONObject(text.toString().trim());
            JSONObject prefs = box.optJSONObject("prefs");
            if (prefs == null) prefs = box;
            SharedPreferences.Editor ed = Prefs.get(this).edit();
            int n = 0;
            for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
                String k = it.next();
                if (isVolatile(k)) continue;
                Object v = prefs.get(k);
                if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                else if (v instanceof Number) ed.putFloat(k, ((Number) v).floatValue());
                else ed.putString(k, String.valueOf(v));
                n++;
            }
            ed.apply();
            cfg = Prefs.load(this);
            toast("导入完成，覆盖了 " + n + " 项设置");
            rebuild();
        } catch (Exception e) {
            toast("这段文字不像配置备份，没敢动现有设置");
        }
    }

    /** 全部恢复出厂设置（密钥也会清掉），只保留桌宠的开着/关着和它现在待的位置。 */
    private void resetAll() {
        boolean on = Prefs.running(this);
        float x = Prefs.posX(this);
        float y = Prefs.posY(this);
        Prefs.get(this).edit().clear().apply();
        Prefs.setRunning(this, on);
        if (x >= 0 && y >= 0) Prefs.setPos(this, x, y);
        cfg = Prefs.load(this);
        toast("已恢复默认设置");
        rebuild();
    }

    /** 运行状态（开关、最近一次余额、窗口位置）不进备份。 */
    private static boolean isVolatile(String key) {
        return "running".equals(key) || "last".equals(key) || "lastinfo".equals(key)
                || "lasterr".equals(key) || "px".equals(key) || "py".equals(key);
    }

    /** 版本号从 manifest 里读，免得和生成脚本里写的不一致。 */
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    // ==================== ① 连接 ====================

    /** 服务商、密钥与接口细节。改完即时存盘，不需要点保存。 */
    private void tabConn() {
        LinearLayout card = ui.card();
        card.addView(ui.cardTitle("余额来源"));
        card.addView(ui.hint("选一个内置服务商，接口细节自动填好；选「自定义」就自己写。"));

        spPreset = ui.choiceRow(card, "服务商", null, Presets.NAMES, Presets.indexOfName(cfg.provider),
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (building || pos == Presets.indexOfName(cfg.provider)) return;
                        cfg.provider = Presets.NAMES[pos];
                        cfg.label = Presets.label(pos);
                        if (!Presets.isCustom(pos)) {
                            cfg.base = Presets.base(pos);
                            cfg.path = Presets.path(pos);
                            cfg.extract = Presets.extract(pos);
                            cfg.curCode = Presets.currency(pos);
                        }
                        apply();
                        rebuild();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> p) {
                    }
                });
        card.addView(ui.hint(Presets.note(Presets.indexOfName(cfg.provider))));
        page.addView(card);

        // ---- 密钥 ----
        LinearLayout keyCard = ui.card();
        keyCard.addView(ui.cardTitle("API Key"));
        etKey = addField(keyCard, "密钥（只存在本机）", cfg.key, new Field() {
            @Override
            public void set(String s) {
                cfg.key = s.trim();
                apply();
            }
        });
        etKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyCard.addView(ui.hint("密钥只保存在本机 SharedPreferences 里，不会上传到任何地方。"));
        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setPadding(0, ui.dp(10), 0, 0);
        btnShowKey = ui.button("显示", false);
        btnShowKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                keyShown = !keyShown;
                etKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | (keyShown ? 0 : android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD));
                etKey.setSelection(etKey.getText().length());
                btnShowKey.setText(keyShown ? "隐藏" : "显示");
            }
        });
        keyRow.addView(btnShowKey);
        ui.buttonRow(keyRow, "立即测试", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testNow();
            }
        });
        keyCard.addView(keyRow);
        tvTest = ui.hint("");
        LinearLayout.LayoutParams tp = ui.wrap();
        tp.topMargin = ui.dp(8);
        tvTest.setLayoutParams(tp);
        keyCard.addView(tvTest);
        page.addView(keyCard);

        // ---- 币种 ----
        LinearLayout curCard = ui.card();
        curCard.addView(ui.cardTitle("币种"));
        curCard.addView(ui.hint("接口没返回币种时用哪个符号。选「自动识别」就看金额里有没有 ¥/$ 之类的符号。"));
        String[] labels = new String[Currencies.NAMES.length];
        for (int i = 0; i < labels.length; i++) labels[i] = Currencies.label(i);
        curCard.addView(ui.gap(4));
        spCurrency = ui.spinner(labels);
        spCurrency.setSelection(Currencies.indexOfCode(cfg.curCode));
        spCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (building) return;
                cfg.curCode = Currencies.code(pos);
                apply();
                fillPreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        curCard.addView(spCurrency);
        final EditText etSym = addField(curCard, "自定义符号（选「自定义」时用）",
                cfg.curCustom, new Field() {
                    @Override
                    public void set(String s) {
                        cfg.curCustom = s;
                        apply();
                    }
                });
        etSym.setVisibility(Currencies.isCustom(Currencies.indexOfCode(cfg.curCode))
                ? View.VISIBLE : View.GONE);
        page.addView(curCard);

        // ---- 接口细节 ----
        LinearLayout adv = ui.card();
        adv.addView(ui.cardTitle("接口细节"));
        adv.addView(ui.hint("换了服务商之后这几项会自动填好，一般不用动。接口不是标准格式时再改。"));
        addField(adv, "Base URL", cfg.base, new Field() {
            @Override
            public void set(String s) {
                cfg.base = s.trim();
                apply();
            }
        });
        addField(adv, "余额接口路径", cfg.path, new Field() {
            @Override
            public void set(String s) {
                cfg.path = s.trim();
                apply();
            }
        });
        addField(adv, "金额字段路径（JSON 点号路径）", cfg.extract, new Field() {
            @Override
            public void set(String s) {
                cfg.extract = s.trim();
                apply();
            }
        });
        addField(adv, "请求头（Name: Value，一行一个）", cfg.header, new Field() {
            @Override
            public void set(String s) {
                cfg.header = s;
                apply();
            }
        });
        addField(adv, "金额前缀", cfg.prefix, new Field() {
            @Override
            public void set(String s) {
                cfg.prefix = s;
                apply();
            }
        });
        page.addView(adv);
    }

    // ==================== ② 气泡 ====================

    /** 气泡的内容、尺寸、配色与出现方式。 */
    private void tabBubble() {
        // ---- 内容 ----
        LinearLayout c1 = ui.card();
        c1.addView(ui.cardTitle("气泡内容"));
        c1.addView(ui.switchRow("显示标题行", "气泡上方那行小字，例如「鲸鱼账户 余额」",
                cfg.showTitle, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        if (building) return;
                        cfg.showTitle = on;
                        applyLive();
                    }
                }));
        c1.addView(ui.divider());
        c1.addView(ui.switchRow("显示币种代码", "在金额后面补一个 CNY / USD 这样的代码，免得 ¥ 认错",
                cfg.showCode, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        if (building) return;
                        cfg.showCode = on;
                        applyLive();
                    }
                }));
        c1.addView(ui.divider());
        addField(c1, "标题里的名字", cfg.label, new Field() {
            @Override
            public void set(String s) {
                cfg.label = s.trim();
                applyLive();
            }
        });
        page.addView(c1);

        // ---- 预览文字：直接看自适应效果 ----
        LinearLayout c2 = ui.card();
        c2.addView(ui.cardTitle("预览文字"));
        c2.addView(ui.hint("在框里随便打点字，上面的气泡会实时跟着改宽度、折行、缩字号。"
                + "试试只打一个「¥1」，或者打一长串字。"));
        sampleInput = addField(c2, "气泡里显示什么", sample, new Field() {
            @Override
            public void set(String s) {
                sample = s;
                fillPreview();
            }
        });
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setPadding(0, ui.dp(10), 0, 0);
        ui.buttonRow(quick, "短金额", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sampleInput.setText("¥33.83");
            }
        });
        ui.buttonRow(quick, "长提示", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sampleInput.setText("查询失败：HTTP 401 Unauthorized");
            }
        });
        ui.buttonRow(quick, "超长", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sampleInput.setText("余额 ¥1234567.89，已经连续 30 天没有充值了，要不要补一点呢？");
            }
        });
        c2.addView(quick);
        page.addView(c2);

        // ---- 尺寸与自适应 ----
        LinearLayout c3 = ui.card();
        c3.addView(ui.cardTitle("尺寸与自适应"));
        c3.addView(ui.hint("气泡先按基准字号排版；排不下就先缩字号，缩到最小字号还排不下就把行数放宽"
                + "（最多 8 行），实在放不下才截断。宽度始终跟着文字走，只有上限由你定。"));
        addSlider(c3, "最大宽度", "气泡最胖能到多宽", 140, 340, cfg.bubbleMaxW,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v + "dp";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleMaxW = v;
                    }
                });
        addSlider(c3, "基准字号", "短文字的默认大小", 14, 30, cfg.bubbleFont,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v + "dp";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleFont = v;
                    }
                });
        addSlider(c3, "最小字号", "长文字缩到这个大小就不再缩了", 10, 22, cfg.bubbleMinFont,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v + "dp";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleMinFont = Math.min(v, cfg.bubbleFont);
                    }
                });
        addSlider(c3, "最多几行", "超过行数就把气泡长高", 1, 6, cfg.bubbleLines,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v + " 行";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleLines = v;
                    }
                });
        addSlider(c3, "内边距", "文字离气泡边缘的空隙，上下按比例跟着走", 6, 22, cfg.bubblePadH,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v + "dp";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubblePadH = v;
                        cfg.bubblePadT = Math.max(4, Math.round(v * 0.6f));
                        cfg.bubblePadB = Math.max(4, Math.round(v * 0.73f));
                    }
                });
        addSlider(c3, "圆角", "0 就是方角", 0, 28, cfg.bubbleRadius,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v == 0 ? "方角" : v + "dp";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleRadius = v;
                    }
                });
        page.addView(c3);

        // ---- 配色 ----
        LinearLayout c4 = ui.card();
        c4.addView(ui.cardTitle("配色"));
        final Ui.Swatches themeSw = ui.swatchRow("主题色", "整个设置页和气泡的强调色",
                Theme.accentPalette(), cfg.theme, new Ui.OnPick() {
                    @Override
                    public void onPick(int i) {
                        cfg.theme = i;
                        apply();
                        rebuild();
                    }
                });
        c4.addView(themeSw.row);
        c4.addView(ui.divider());
        final Ui.Segment darkSeg = ui.segment(Theme.MODE_NAMES, cfg.dark, new Ui.OnPick() {
            @Override
            public void onPick(int i) {
                cfg.dark = i;
                apply();
                rebuild();
            }
        });
        c4.addView(ui.titled("深浅模式", "设置页和气泡的底色，跟随系统会跟着手机的黑夜模式走"));
        LinearLayout.LayoutParams dp1 = ui.wrap();
        dp1.topMargin = ui.dp(8);
        darkSeg.row.setLayoutParams(dp1);
        c4.addView(darkSeg.row);
        c4.addView(ui.divider());
        final Ui.Swatches bgSw = ui.swatchRow("气泡底色", "选「自动」就跟着深浅模式走",
                Theme.SWATCH, cfg.bubbleBg, new Ui.OnPick() {
                    @Override
                    public void onPick(int i) {
                        cfg.bubbleBg = i;
                        applyLive();
                    }
                });
        c4.addView(bgSw.row);
        addSlider(c4, "不透明度", "让气泡半透明一点", 40, 100, Math.round(cfg.bubbleAlpha * 100f),
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v + "%";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleAlpha = v / 100f;
                    }
                });
        c4.addView(ui.switchRow("柔和阴影", "气泡底下加一层淡淡投影，浅色背景上更清楚",
                cfg.bubbleShadow, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        if (building) return;
                        cfg.bubbleShadow = on;
                        applyLive();
                    }
                }));
        c4.addView(ui.divider());
        addSlider(c4, "尖角长度", "气泡指着角色的那个小三角，0 就是不要尖角", 0, 16, cfg.bubbleTail,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v == 0 ? "无尖角" : v + "dp";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleTail = v;
                    }
                });
        final Ui.Segment tailSeg = ui.segment(new String[]{"尖角朝下", "尖角朝上"},
                cfg.bubbleTailUp ? 1 : 0, new Ui.OnPick() {
                    @Override
                    public void onPick(int i) {
                        cfg.bubbleTailUp = i == 1;
                        applyLive();
                    }
                });
        c4.addView(ui.titled("尖角方向", "朝下＝气泡挂在角色头顶；朝上＝角色在上、气泡在下"));
        LinearLayout.LayoutParams tp2 = ui.wrap();
        tp2.topMargin = ui.dp(8);
        tailSeg.row.setLayoutParams(tp2);
        c4.addView(tailSeg.row);
        page.addView(c4);

        // ---- 出现方式 ----
        LinearLayout c5 = ui.card();
        c5.addView(ui.cardTitle("出现方式"));
        addSlider(c5, "停留时长", "自动收起前停留多久", 2, 30, cfg.bubbleHideMs / 1000,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v + " 秒";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleHideMs = v * 1000;
                    }
                });
        c5.addView(ui.switchRow("弹出动画", "气泡大小变化时走一段过场，关掉就是瞬间出现",
                cfg.bubbleAnim, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        if (building) return;
                        cfg.bubbleAnim = on;
                        applyLive();
                    }
                }));
        addSlider(c5, "动画时长", "过场走多久", 60, 500, cfg.bubbleAnimMs,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v + "ms";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.bubbleAnimMs = v;
                    }
                });
        page.addView(c5);
    }

    // ==================== ③ 角色 ====================

    /** 模式、外观、互动方式与刷新节奏。 */
    private void tabPet() {
        LinearLayout c1 = ui.card();
        c1.addView(ui.cardTitle("模式"));
        final Ui.Segment modeSeg = ui.segment(Prefs.MODE_NAMES, cfg.mode, new Ui.OnPick() {
            @Override
            public void onPick(int i) {
                cfg.mode = i;
                apply();
                rebuild();
            }
        });
        LinearLayout.LayoutParams mp = ui.wrap();
        mp.topMargin = ui.dp(8);
        modeSeg.row.setLayoutParams(mp);
        c1.addView(modeSeg.row);
        c1.addView(ui.hint(Prefs.modeNote(cfg.mode)));
        page.addView(c1);

        LinearLayout c2 = ui.card();
        c2.addView(ui.cardTitle("角色"));
        c2.addView(ui.switchRow("显示角色", "关掉就只剩一个气泡挂在桌面上",
                cfg.showChar, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        if (building) return;
                        cfg.showChar = on;
                        applyLive();
                    }
                }));
        c2.addView(ui.divider());
        addSlider(c2, "角色大小", "屏幕上占多宽", 64, 140, cfg.charSize, new Ui.Fmt() {
            @Override
            public String text(int v) {
                return v + "dp";
            }
        }, new IntSetter() {
            @Override
            public void set(int v) {
                cfg.charSize = v;
            }
        });
        c2.addView(ui.switchRow("角色动作", "眨眼、甩尾、挥手这些骨架形变，关掉就是一张静态立绘",
                cfg.animate, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        if (building) return;
                        cfg.animate = on;
                        applyLive();
                    }
                }));
        c2.addView(ui.divider());
        final Ui.Segment idleSeg = ui.segment(Prefs.IDLE_NAMES, cfg.petIdle, new Ui.OnPick() {
            @Override
            public void onPick(int i) {
                cfg.petIdle = i;
                applyLive();
            }
        });
        c2.addView(ui.titled("活泼度", "越活泼，待机时自己动一动的次数越多"));
        LinearLayout.LayoutParams ip = ui.wrap();
        ip.topMargin = ui.dp(8);
        idleSeg.row.setLayoutParams(ip);
        c2.addView(idleSeg.row);
        page.addView(c2);

        LinearLayout c3 = ui.card();
        c3.addView(ui.cardTitle("互动"));
        ui.choiceRow(c3, "点击角色", "用「按模式」最省心：桌宠模式说句话，token 模式刷新余额",
                Prefs.TAP_NAMES, cfg.tapAction, new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (building) return;
                        cfg.tapAction = pos;
                        apply();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> p) {
                    }
                });
        ui.choiceRow(c3, "长按角色", "默认长按打开这个设置页",
                Prefs.LONG_NAMES, cfg.longTapAction, new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (building) return;
                        cfg.longTapAction = pos;
                        apply();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> p) {
                    }
                });
        addSlider(c3, "自动说话间隔", "0 就是不主动说话", 0, 30, cfg.talkSec / 15,
                new Ui.Fmt() {
                    @Override
                    public String text(int v) {
                        return v == 0 ? "不主动说话" : (v * 15) + " 秒";
                    }
                }, new IntSetter() {
                    @Override
                    public void set(int v) {
                        cfg.talkSec = v * 15;
                    }
                });
        page.addView(c3);

        LinearLayout c4 = ui.card();
        c4.addView(ui.cardTitle("刷新"));
        addSlider(c4, "自动刷新间隔", "隔多久悄悄查一次余额", 1, 60, cfg.interval, new Ui.Fmt() {
            @Override
            public String text(int v) {
                return v + " 分钟";
            }
        }, new IntSetter() {
            @Override
            public void set(int v) {
                cfg.interval = v;
            }
        });
        c4.addView(ui.switchRow("余额变化提醒", "混合模式下静默刷新时发现金额变了，主动冒个泡",
                cfg.notice, new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        if (building) return;
                        cfg.notice = on;
                        apply();
                    }
                }));
        page.addView(c4);
    }

    // ==================== ④ 关于 ====================

    /** 用法说明、配置导入导出和重置。 */
    private void tabAbout() {
        LinearLayout c1 = ui.card();
        c1.addView(ui.cardTitle("怎么用"));
        c1.addView(ui.text("• 拖动角色可以把它挪到屏幕任意位置，松手就记住\n"
                + "• 点角色按上面设好的动作走；气泡开着的时候点气泡＝立刻收起\n"
                + "• 长按角色默认打开这个设置页\n"
                + "• 想让角色彻底消失，先把「显示角色」关掉，再关掉顶部的主开关", 13f, ui.themeTxt()));
        page.addView(c1);

        LinearLayout c2 = ui.card();
        c2.addView(ui.cardTitle("权限与隐私"));
        c2.addView(ui.text("• 需要「显示在其他应用上层」才能把角色浮在桌面上\n"
                + "• 需要「通知」权限只是为了前台服务不被系统杀掉，不会有推送\n"
                + "• API Key 只写在本机 SharedPreferences，不会发给任何第三方", 13f, ui.themeTxt()));
        page.addView(c2);

        LinearLayout c3 = ui.card();
        c3.addView(ui.cardTitle("配置搬家"));
        c3.addView(ui.hint("把当前全部设置导成一段文本，换手机或者想留个底的时候很有用。"
                + "密钥也会一起导出，注意别外传。"));
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, ui.dp(10), 0, 0);
        ui.buttonRow(row1, "复制配置", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyConfig();
            }
        });
        ui.buttonRow(row1, "从剪贴板导入", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importConfig();
            }
        });
        c3.addView(row1);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, ui.dp(4), 0, 0);
        ui.buttonRow(row2, "角色归位", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.clearPos(MainActivity.this);
                send(BubbleService.ACTION_RESET_POS);
                toast("已经挪回屏幕中间");
            }
        });
        ui.buttonRow(row2, "恢复默认设置", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAll();
            }
        });
        c3.addView(row2);
        page.addView(c3);

        LinearLayout c4 = ui.card();
        c4.addView(ui.cardTitle("版本"));
        c4.addView(ui.text("鲸鱼娘桌宠 " + versionName(), 13.5f, ui.themeTxt()));
        c4.addView(ui.hint("主题、气泡尺寸、互动方式都在这里改，改完立刻生效，不用重启应用。"));
        page.addView(c4);
    }
}
