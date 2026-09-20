package com.coco.balancebubble;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * 开机自启：仅在用户上次开着气泡、且已授权悬浮窗、且已保存密钥时恢复。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(a)) return;
        if (!Prefs.running(ctx)) return;
        if (Prefs.key(ctx).isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) return;
        try {
            Intent i = new Intent(ctx, BubbleService.class);
            i.setAction(BubbleService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }
}
