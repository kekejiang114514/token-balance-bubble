package com.coco.balancebubble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.InputStream;

public class BubbleService extends Service {

    public static final String ACTION_START = "com.coco.balancebubble.START";
    public static final String ACTION_STOP = "com.coco.balancebubble.STOP";
    public static final String ACTION_REFRESH = "com.coco.balancebubble.REFRESH";
    /** 把气泡放回默认位置（忘记拖拽记录） */
    public static final String ACTION_RESET_POS = "com.coco.balancebubble.RESET_POS";

    private static final String CH_ID = "bubble";
    private static final int NOTI_ID = 8848;

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private BubbleView bubble;
    private ImageView charView;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private boolean refreshing = false;
    private boolean dragging = false;
    /** 是否已经完成首次定位（避免布局回调把手动位置冲掉） */
    private boolean placed = false;

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
            ui.removeCallbacks(tick);
            ui.post(tick);
            return START_STICKY;
        }
        Prefs.setRunning(this, true);
        if (root == null) {
            show();
        } else {
            applyCharVisibility();
            if (placed) root.post(new Runnable() {
                @Override
                public void run() {
                    keepInsideScreen();
                }
            });
        }
        ui.removeCallbacks(tick);
        ui.post(tick);
        return START_STICKY;
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CH_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CH_ID, "余额查询",
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
        b.setContentTitle("余额查询运行中")
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

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        bubble = new BubbleView(this);
        col.addView(bubble);

        charView = new ImageView(this);
        Bitmap bm = loadChar();
        if (bm != null) {
            charView.setImageBitmap(bm);
            int cw = (int) dp(Prefs.charSize(this));
            int ch = Math.max(1, (int) (cw * (float) bm.getHeight() / bm.getWidth()));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cw, ch);
            cp.topMargin = (int) -dp(10);
            charView.setLayoutParams(cp);
        } else {
            charView.setVisibility(View.GONE);
        }
        col.addView(charView);
        root.addView(col);

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

    private void applyCharVisibility() {
        if (charView == null) return;
        boolean show = Prefs.showChar(this);
        charView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            Bitmap bm = loadChar();
            if (bm != null) {
                int cw = (int) dp(Prefs.charSize(this));
                int ch = Math.max(1, (int) (cw * (float) bm.getHeight() / bm.getWidth()));
                LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) charView.getLayoutParams();
                cp.width = cw;
                cp.height = ch;
                charView.setLayoutParams(cp);
            }
        }
    }

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
                        } else {
                            refresh();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
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
            openSettings();
        }
    };

    private void openSettings() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.removeCallbacks(tick);
            ui.postDelayed(tick, Math.max(1, Prefs.interval(BubbleService.this)) * 60000L);
        }
    };

    private void refresh() {
        if (refreshing) return;
        if (root == null || bubble == null) return;
        if (Prefs.key(this).isEmpty()) {
            bubble.setData("请先在设置里填 API Key", "—", true);
            return;
        }
        refreshing = true;
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
        if (bubble == null) return;
        if (r.ok) {
            String name = Prefs.label(this).trim();
            String label = name.isEmpty() ? "" : name + " 余额";
            String text = r.currency + r.amount;
            bubble.setData(label, text, false);
            Prefs.setLast(this, text);
            Prefs.setLastErr(this, "");
        } else {
            String msg = r.error == null ? "查询失败" : r.error;
            String first = msg.split("\n")[0];
            bubble.setData(first, "—", true);
            Prefs.setLastErr(this, msg);
        }
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
    public void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (root != null && wm != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            root = null;
        }
        super.onDestroy();
    }
}



