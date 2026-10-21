package com.simpletv.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Opens Simple TV when the TV starts, so the Amazon home screen isn't the first thing you see. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        context.startActivity(new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
