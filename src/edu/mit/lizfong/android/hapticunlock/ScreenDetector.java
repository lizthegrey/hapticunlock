package edu.mit.lizfong.android.hapticunlock;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

public class ScreenDetector extends Service {

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    super.onStartCommand(intent, flags, startId);
    return Service.START_STICKY;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    receiver = new ScreenReceiver();
    registerReceiver(receiver, filter);
  }
  
  @Override
  public void onDestroy() {
    unregisterReceiver(receiver);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return null;
  }

  public class ScreenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
        UnlockActivity.locked = true;
      } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON) ||
                  intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
        Intent lockscreen = new Intent(ScreenDetector.this, UnlockActivity.class);
        lockscreen.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        lockscreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        UnlockActivity.root_home = false;
        context.startActivity(lockscreen);
      }
    }

  }
  private ScreenReceiver receiver;
}

