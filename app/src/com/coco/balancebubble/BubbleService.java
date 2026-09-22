package com.coco.balancebubble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * 悬浮窗服务：角色常驻，气泡默认收起。
 *
 * <p>token 查询模式：点一下角色刷新余额，气泡里先显示「正在刷新中…」再显示结果。
 * 桌宠模式：点一下角色随机说一句卖萌话，并且每分钟自动说一句。
 */
public class BubbleService extends Service {

    public static final String ACTION_START = "com.coco.balancebubble.START";
    public static final String ACTION_STOP = "com.coco.balancebubble.STOP";
    public static final String ACTION_REFRESH = "com.coco.balancebubble.REFRESH";
    /** 把气泡放回默认位置（忘记拖拽记录） */
    public static final String ACTION_RESET_POS = "com.coco.balancebubble.RESET_POS";

    private static final String CH_ID = "bubble";
    private static final int NOTI_ID = 8848;

    /** 结果出来后气泡停留多久再自动收起的默认值（可在设置里改） */
    private static final long AUTO_HIDE_MS = 7000L;

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private LinearLayout column;
    private BubbleView bubble;
    private PetView charView;
    /** 当前生效的气泡外观（设置页改完会重建） */
    private BubbleStyle style;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /**
     * 设置一变就重排悬浮窗。
     *
     * <p>这里监听 SharedPreferences，而不是等设置页推 Intent 过来：设置页那边的推送
     * 依赖「服务在跑」这个判断，一旦标记失准（比如服务被系统杀过、设置页没拿到状态），
     * 改什么都不会落到窗口上。监听是进程内的，改完必到。
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener prefWatch =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (root == null) return;
                    if (Prefs.isRuntimeKey(key)) return;  // 位置/余额这类自己写的键，回灌会打架
                    ui.removeCallbacks(restyle);
                    ui.postDelayed(restyle, 60);          // 拖滑杆时合并成一次重排
                }
            };

    private final Runnable restyle = new Runnable() {
        @Override
        public void run() {
            if (root == null) return;
            applyStyle();
            restartTimers();
        }
    };

    private boolean refreshing = false;
    private boolean dragging = false;
    /** 是否已经完成首次定位（避免布局回调把手动位置冲掉） */
    private boolean placed = false;
    /** 正在飞的这次请求，结果要不要弹出来给用户看 */
    private boolean revealResult = false;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            Prefs.setRunning(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTI_ID, buildNotification());
        if (ACTION_RESET_POS.equals(action)) {
            Prefs.clearPos(this);
            if (root != null && lp != null) {
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        placeInitial();
                    }
                });
            }
            return START_STICKY;
        }
        if (ACTION_REFRESH.equals(action)) {
            if (root == null) show();
            restartTimers();
            return START_STICKY;
        }
        Prefs.setRunning(this, true);
        if (root == null) {
            show();
        } else {
            applyStyle();
            if (placed) root.post(new Runnable() {
                @Override
                public void run() {
                    keepInsideScreen();
                }
            });
        }
        restartTimers();
        return START_STICKY;
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CH_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CH_ID, "桌宠",
                        NotificationManager.IMPORTANCE_MIN);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
        Intent open = new Intent(this, MainActivity.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CH_ID);
        } else {
            b = new Notification.Builder(this);
        }
        int mode = Prefs.mode(this);
        String title = mode == Prefs.MODE_TOKEN ? "余额查询运行中"
                : (mode == Prefs.MODE_PET ? "鲸鱼娘桌宠运行中" : "鲸鱼娘桌宠 · 混合模式");
        b.setContentTitle(title)
                .setContentText("点这里回到设置")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pi)
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 21) b.setPriority(Notification.PRIORITY_MIN);
        return b.build();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void show() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        root = new FrameLayout(this);
        style = BubbleStyle.from(this);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        bubble = new BubbleView(this, style);
        // 气泡平时收起：用 INVISIBLE 而不是 GONE，窗口尺寸保持恒定，
        // 弹出/收起时角色不会上下跳动。
        bubble.setVisibility(View.INVISIBLE);

        charView = new PetView(this);
        charView.setAnimated(Prefs.animate(this));
        charView.setIdleLevel(Prefs.petIdle(this));
        layoutColumn();
        root.addView(column);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = 0;

        setupDrag();
        applyCharVisibility();
        // 文字或角色尺寸变化会让窗口重新测量；此时把位置拉回屏幕内，
        // 但首次定位之前不干预，否则会把默认位置冲成 (0,0)。
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                if (!placed) return;
                if ((r - l) == (or - ol) && (b - t) == (ob - ot)) return;
                keepInsideScreen();
            }
        });
        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            Prefs.setRunning(this, false);
            stopSelf();
            return;
        }
        // 窗口真的挂上去了才认「在跑」——设置页拿这个标记决定要不要推送改动。
        Prefs.setRunning(this, true);
        root.post(new Runnable() {
            @Override
            public void run() {
                placeInitial();
            }
        });
    }

    private void placeInitial() {
        if (root == null || lp == null || wm == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(1, root.getWidth());
        int h = Math.max(1, root.getHeight());
        int px = (int) Prefs.posX(this), py = (int) Prefs.posY(this);
        if (px < 0 || py < 0) {
            px = (dm.widthPixels - w) / 2;
            py = Math.max(0, dm.heightPixels - h - (int) dp(110));
        }
        lp.x = clamp(px, 0, Math.max(0, dm.widthPixels - w));
        lp.y = clamp(py, 0, Math.max(0, dm.heightPixels - h));
        try {
            wm.updateViewLayout(root, lp);
        } catch (Exception ignored) {
        }
        placed = true;
    }

    private int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /** 角色画布：宽度＝设置里的「角色大小」，高度留出起跳净空。 */
    private LinearLayout.LayoutParams charParams() {
        int cw = (int) dp(Prefs.charSize(this));
        int ch = (int) (cw * PetView.VIEW_H_RATIO);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cw, ch);
        // 角色和气泡贴紧一点：气泡在下时，往上收；气泡在上时，往下收。
        boolean tailUp = style != null && style.tailUp;
        if (tailUp) {
            cp.topMargin = (int) -dp(10);
        } else {
            cp.topMargin = (int) -dp(6);
        }
        return cp;
    }

    /**
     * 按「尖角朝哪边」决定气泡在角色的上方还是下方。
     *
     * <p>气泡默认挂在角色头顶（尖角朝下、指着角色）；把「尖角朝上」打开后，
     * 角色在上、气泡在下，适合把小鲸鱼放到屏幕上半部分的摆法。
     */
    private void layoutColumn() {
        if (column == null || bubble == null || charView == null) return;
        boolean tailUp = style != null && style.tailUp;
        column.removeAllViews();
        if (tailUp) {
            column.addView(charView, charParams());
            column.addView(bubble);
        } else {
            column.addView(bubble);
            column.addView(charView, charParams());
        }
    }

    /** 设置页改完外观后调用：重建样式并立刻套到已经挂着的视图上。 */
    private void applyStyle() {
        style = BubbleStyle.from(this);
        if (bubble != null) bubble.applyStyle(style);
        if (charView != null) {
            charView.setAnimated(Prefs.animate(this));
            charView.setIdleLevel(Prefs.petIdle(this));
        }
        layoutColumn();
        applyCharVisibility();
    }

    private void applyCharVisibility() {
        if (charView == null) return;
        boolean show = Prefs.showChar(this);
        charView.setVisibility(show ? View.VISIBLE : View.GONE);
        charView.setLayoutParams(charParams());
    }

    /** 播放一个动作（角色关掉或动作开关关掉时什么也不做）。 */
    private void playAction(PetAction a) {
        if (charView == null || !Prefs.animate(this)) return;
        charView.play(a);
    }

    // ==================== 气泡的弹出与收起 ====================

    /** 随机说一句卖萌话，同时播放配套动作。 */
    private void speak() {
        say(PetTalk.random());
    }

    /** 说一句话（气泡会自动按字数折行），过一会儿自动收起。 */
    private void say(PetTalk.Line line) {
        if (line == null) return;
        playAction(line.action);
        if (bubble == null) return;
        bubble.setAmountScale(phraseScale(line.text));
        bubble.setData("", line.text, false);
        revealBubble(hideMs());
    }

    /** 气泡停留时长（设置里可调）。 */
    private long hideMs() {
        return Prefs.bubbleHideMs(this);
    }

    /**
     * 长句用稍小的基准字号，短句用大字号。气泡宽度本来就跟着文字走，
     * 所以这里只是「语气」上的大小差别，不会改变能不能放下。
     */
    private float phraseScale(String text) {
        int n = text == null ? 0 : text.length();
        if (n > 26) return 0.72f;
        if (n > 14) return 0.85f;
        return 1f;
    }

    /** 弹出气泡，并在指定时长后自动收起。 */
    private void revealBubble(long hideAfterMs) {
        if (bubble == null) return;
        ui.removeCallbacks(hideBubble);
        bubble.setVisibility(View.VISIBLE);
        if (hideAfterMs > 0) ui.postDelayed(hideBubble, hideAfterMs);
    }

    private final Runnable hideBubble = new Runnable() {
        @Override
        public void run() {
            if (bubble == null) return;
            bubble.setVisibility(View.INVISIBLE);
        }
    };

    /** 刷新中：气泡立刻弹出并显示状态，查询期间不收起。 */
    private void showBusy() {
        if (bubble == null) return;
        ui.removeCallbacks(hideBubble);
        bubble.setAmountScale(0.82f);
        bubble.setData(balanceLabel(), "正在刷新中…", false);
        bubble.setVisibility(View.VISIBLE);
    }

    /** 气泡第一行的小字：填了名字就是「xxx 余额」，没填就只显示金额。 */
    private String balanceLabel() {
        if (!Prefs.showTitle(this)) return "";
        String name = Prefs.label(this).trim();
        return name.isEmpty() ? "" : name + " 余额";
    }

    // ==================== 点击与拖拽 ====================

    private void setupDrag() {
        root.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging = false;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        ui.postDelayed(longPress, 550);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > dp(7)) {
                            dragging = true;
                            ui.removeCallbacks(longPress);
                        }
                        if (dragging) {
                            lp.x = startX + (int) dx;
                            lp.y = startY + (int) dy;
                            try {
                                wm.updateViewLayout(root, lp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ui.removeCallbacks(longPress);
                        if (dragging) {
                            // 这里以前先调 placeInitial()，而它读的是**旧的**存储坐标，
                            // 于是每次松手都被拉回默认位置。现在直接把当前位置收边后落盘。
                            dragging = false;
                            clampAndSave();
                        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                            onTap(e.getRawX(), e.getRawY());
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /** 点一下：具体干什么由设置里的「点击角色」决定。 */
    private void onTap(float rawX, float rawY) {
        // 气泡正开着的时候，点气泡＝立刻收起（这个不受设置影响，永远生效）。
        if (bubble != null && bubble.getVisibility() == View.VISIBLE && inside(bubble, rawX, rawY)) {
            ui.removeCallbacks(hideBubble);
            hideBubble.run();
            return;
        }
        // 只有点在角色身上才算数（角色关掉时整个窗口都可以点）。
        if (!inside(charView, rawX, rawY)) return;
        switch (Prefs.tapAction(this)) {
            case Prefs.TAP_TALK:
                speak();
                break;
            case Prefs.TAP_REFRESH:
                playAction(PetAction.NOD);
                refresh(true);
                break;
            case Prefs.TAP_BUBBLE:
                toggleBubble();
                break;
            case Prefs.TAP_MODE:
            default:
                if (Prefs.queriesBalance(this)) {
                    // 混合模式点一下＝查余额；单 token 模式点一下也只是查余额。
                    playAction(Prefs.mode(this) == Prefs.MODE_MIXED ? PetAction.LEAN : PetAction.NOD);
                    refresh(true);
                } else {
                    speak();
                }
                break;
        }
    }

    /** 点一下就把气泡翻出来 / 收回去。 */
    private void toggleBubble() {
        if (bubble == null) return;
        if (bubble.getVisibility() == View.VISIBLE) {
            ui.removeCallbacks(hideBubble);
            hideBubble.run();
            return;
        }
        String last = Prefs.lastAmount(this);
        if (last == null || last.isEmpty() || "--".equals(last)) {
            speak();
            return;
        }
        playAction(PetAction.LEAN);
        bubble.setAmountScale(1f);
        bubble.setData(balanceLabel(), last, Prefs.lastError(this));
        revealBubble(hideMs());
    }

    /** 判断触点是否落在某个视图上，留一点容差方便点中。 */
    private boolean inside(View v, float rawX, float rawY) {
        // 视图不存在或没显示时按「整块区域」处理，免得角色关掉后点哪都没反应。
        if (v == null || v.getVisibility() != View.VISIBLE) return true;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float pad = dp(10);
        return rawX >= loc[0] - pad && rawX <= loc[0] + v.getWidth() + pad
                && rawY >= loc[1] - pad && rawY <= loc[1] + v.getHeight() + pad;
    }

    /** 让窗口待在屏幕内，并把当前位置记住。 */
    private void clampAndSave() {
        if (root == null || lp == null || wm == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(1, root.getWidth());
        int h = Math.max(1, root.getHeight());
        lp.x = clamp(lp.x, 0, Math.max(0, dm.widthPixels - w));
        lp.y = clamp(lp.y, 0, Math.max(0, dm.heightPixels - h));
        try {
            wm.updateViewLayout(root, lp);
        } catch (Exception ignored) {
        }
        Prefs.setPos(this, lp.x, lp.y);
    }

    /** 金额文字变长、角色开关等导致窗口变尺寸时，避免气泡被挤出屏幕。 */
    private void keepInsideScreen() {
        if (root == null || lp == null || wm == null || dragging) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(1, root.getWidth());
        int h = Math.max(1, root.getHeight());
        int nx = clamp(lp.x, 0, Math.max(0, dm.widthPixels - w));
        int ny = clamp(lp.y, 0, Math.max(0, dm.heightPixels - h));
        if (nx != lp.x || ny != lp.y) {
            lp.x = nx;
            lp.y = ny;
            try {
                wm.updateViewLayout(root, lp);
            } catch (Exception ignored) {
            }
            Prefs.setPos(this, lp.x, lp.y);
        }
    }

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            dragging = true;
            switch (Prefs.longTapAction(BubbleService.this)) {
                case Prefs.LONG_HIDE:
                    ui.removeCallbacks(hideBubble);
                    hideBubble.run();
                    break;
                case Prefs.LONG_NONE:
                    break;
                case Prefs.LONG_SETTINGS:
                default:
                    openSettings();
                    break;
            }
        }
    };

    private void openSettings() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ==================== 定时任务 ====================

    /**
     * 按当前模式重排定时任务。两个定时器互相独立：
     * 余额刷新按「自动刷新间隔」，说话按设置里的「自动说话间隔」（0 表示不说），
     * 混合模式下两者同时在跑。
     */
    private void restartTimers() {
        ui.removeCallbacks(tickBalance);
        ui.removeCallbacks(tickTalk);
        if (Prefs.queriesBalance(this)) ui.postDelayed(tickBalance, 1500L);
        long talk = Prefs.talkSec(this) * 1000L;
        if (Prefs.speaks(this) && talk > 0) ui.postDelayed(tickTalk, talk);
    }

    /** 定时静默刷新余额（不弹气泡，只有金额变了才在混合模式里冒泡）。 */
    private final Runnable tickBalance = new Runnable() {
        @Override
        public void run() {
            if (!Prefs.queriesBalance(BubbleService.this)) return;
            refresh(false);
            ui.postDelayed(tickBalance,
                    Math.max(1, Prefs.interval(BubbleService.this)) * 60000L);
        }
    };

    /** 定时说话＋做动作。 */
    private final Runnable tickTalk = new Runnable() {
        @Override
        public void run() {
            long talk = Prefs.talkSec(BubbleService.this) * 1000L;
            if (!Prefs.speaks(BubbleService.this) || talk <= 0) return;
            speak();
            ui.postDelayed(tickTalk, talk);
        }
    };

    /**
     * 查询余额。
     *
     * @param reveal true 表示用户点的，要把气泡弹出来（先显示「正在刷新中…」）；
     *               false 表示定时静默刷新，只更新数据不打扰用户。
     */
    private void refresh(boolean reveal) {
        if (root == null || bubble == null) return;
        if (Prefs.key(this).isEmpty()) {
            if (reveal) {
                bubble.setAmountScale(0.82f);
                bubble.setData("", "还没填 API Key 哦", true);
                revealBubble(hideMs());
            }
            return;
        }
        if (refreshing) {
            // 已有请求在飞：把气泡弹出来等结果就好，不必重复发请求。
            if (reveal) {
                revealResult = true;
                showBusy();
            }
            return;
        }
        refreshing = true;
        revealResult = reveal;
        if (reveal) showBusy();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BalanceApi.Result r = BalanceApi.query(BubbleService.this);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshing = false;
                        applyResult(r);
                    }
                });
            }
        }).start();
    }

    private void applyResult(BalanceApi.Result r) {
        boolean reveal = revealResult;
        revealResult = false;
        if (bubble == null) return;
        String prev = Prefs.lastAmount(this);
        if (r.ok) {
            String text = r.currency + r.amount;
            bubble.setAmountScale(1f);
            bubble.setData(balanceLabel(), text, false);
            Prefs.setLast(this, text);
            Prefs.setLastErr(this, "");
            // 混合模式：静默刷新发现金额变了，主动冒泡提醒一下。
            if (!reveal && Prefs.notice(this) && Prefs.mode(this) == Prefs.MODE_MIXED
                    && prev != null && !prev.isEmpty() && !"--".equals(prev)
                    && !prev.equals(text)) {
                playAction(PetAction.POP);
                bubble.setAmountScale(0.95f);
                bubble.setData("余额有变化", text, false);
                revealBubble(hideMs() + 2500L);
            }
        } else {
            String msg = r.error == null ? "查询失败" : r.error;
            String first = msg.split("\n")[0];
            bubble.setAmountScale(0.72f);
            bubble.setData("", first, true);
            Prefs.setLastErr(this, msg);
        }
        if (reveal) revealBubble(hideMs());
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if (root != null) {
            root.post(new Runnable() {
                @Override
                public void run() {
                    keepInsideScreen();
                }
            });
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefWatch);
    }

    @Override
    public void onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefWatch);
        ui.removeCallbacks(restyle);
        ui.removeCallbacksAndMessages(null);
        if (root != null && wm != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            root = null;
        }
        // 窗口已经摘掉了，标记跟着落地，免得设置页以为桌宠还开着。
        Prefs.setRunning(this, false);
        super.onDestroy();
    }
}
