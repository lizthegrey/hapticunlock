package edu.mit.lizfong.android.hapticunlock;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.mit.lizfong.android.hapticunlock.R;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnDragListener;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class UnlockActivity extends Activity {
  private class TouchListener implements View.OnTouchListener, View.OnHoverListener {
    AccessibilityManager am = (AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);

    public TouchListener(View parent, Handler handle) {
      parent_ = parent;
      handle_ = handle;
    }

    @Override
    public boolean onTouch(View view, MotionEvent ev) {
        switch (ev.getAction()) {
          case MotionEvent.ACTION_DOWN:
            if (!am.isTouchExplorationEnabled()) {
              handle_.postDelayed(longtouch, 200);
            } else {
              if (!handleElement((Integer)parent_.getTag())) {
                v.cancel();
                v.vibrate(300);
              }
            }
            break;

          case MotionEvent.ACTION_CANCEL:
            handle_.removeCallbacks(longtouch);
            break;

          case MotionEvent.ACTION_UP:
            handle_.removeCallbacks(longtouch);
            break;
        }
        return true;
    }

    @Override
    public boolean onHover(View view, MotionEvent ev) {
      switch (ev.getAction()) {
        case MotionEvent.ACTION_HOVER_ENTER:
          if (!am.isTouchExplorationEnabled()) {
            handle_.postDelayed(longtouch, 200);
          } else {
            doItemVibration((Integer)parent_.getTag());
          }
          break;

        case MotionEvent.ACTION_HOVER_EXIT:
          if (!am.isTouchExplorationEnabled()) {
            handle_.removeCallbacks(longtouch);
          } else {
            v.cancel();
          }
          break;
      }
      return !am.isTouchExplorationEnabled();
    }

    private View parent_;
    private Handler handle_;

    private Runnable longtouch = new Runnable() {
      @Override
      public void run() {
        if (elementActive) {
          return;
        }

        elementActive = true;

        int itemNumber = (Integer)parent_.getTag();
        ClipData dragData = ClipData.newPlainText("label", "" + itemNumber);
        View.DragShadowBuilder myShadow = new DragShadowBuilder(parent_);
        parent_.startDrag(dragData, myShadow,
                    null, // no need to use local data
                    0 // flags (not currently used, set to 0)
        );
        doItemVibration(itemNumber);
      }
    };

    static final String salt = "hapticfeedback!";

    private void doItemVibration(int itemNumber) {
      long[] pattern = {100, 200, 100, 400};
      try {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        int len = md.getDigestLength();
        pattern = new long[len];
        byte[] result = md.digest((salt + itemNumber).getBytes());
        for (int i = 0; i < result.length; i++) {
          pattern[i] = result[i] * 2;
        }
      } catch (NoSuchAlgorithmException e) {
        // allow default pattern.
      }
      v.cancel();
      v.vibrate(pattern, 0);
    }
  }

  private class PasswordDropListener implements OnDragListener {
    public boolean onDrag(View view, DragEvent event) {
      ImageView iv = (ImageView)view;
      switch (event.getAction()) {
        case DragEvent.ACTION_DRAG_STARTED:
          // Determines if this View can accept the dragged data
          if (event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            iv.setColorFilter(Color.BLUE);
            iv.invalidate();
            return true;
          } else {
            // Returns false. During the current drag and drop operation, this View will
            // not receive events again until ACTION_DRAG_ENDED is sent.
            return false;
          }

        case DragEvent.ACTION_DRAG_ENTERED:
          iv.setColorFilter(Color.GREEN);
          iv.invalidate();
          return true;

        case DragEvent.ACTION_DRAG_LOCATION:
          return true;

        case DragEvent.ACTION_DRAG_EXITED:
          iv.setColorFilter(Color.BLUE);
          iv.invalidate();
          return true;

        case DragEvent.ACTION_DROP:
          ClipData.Item item = event.getClipData().getItemAt(0);
          int receivedElement = Integer.parseInt(item.getText().toString());
          v.cancel();

          if (!handleElement(receivedElement)) {
            v.vibrate(300);
          }
          iv.clearColorFilter();
          iv.invalidate();
          return true;

        case DragEvent.ACTION_DRAG_ENDED:
          iv.clearColorFilter();
          iv.invalidate();
          
          elementActive = false;
          if (!event.getResult()) {
            v.cancel();
          }
          return true;
        // An unknown action type was received.
        default:
          Log.e("UnlockActivity", "Unknown action type received by PasswordDropListener.");
          return true;
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    root_home = true;
    startService(new Intent(this, ScreenDetector.class));
    setContentView(R.layout.activity_unlock);
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

    final View dropper = findViewById(R.id.dropper);

    dropper.setOnDragListener(new PasswordDropListener());
    clearAndAddRandomElements();
  }
  
  @Override
  protected void onResume() {
    if (!locked) {
      if (root_home) {
        InvokeStockLauncher();
      } else {
        finish();
      }
    }
    super.onResume();
  }

  @Override
  public void onBackPressed() {
    // Turn off back button.
  }

  void clearAndAddRandomElements() {
    LinearLayout chooser = (LinearLayout) findViewById(R.id.chooser);
    chooser.removeAllViews();

    ArrayList<Integer> order = new ArrayList<Integer>(); 

    for (int i = 0; i < numItems; i++) {
      order.add(i);
    }
    Collections.shuffle(order);
    
    for (Integer i : order) {
      TextView option = new TextView(this);
      chooser.addView(option);
      option.setTag(i);
      option.setText("" + i);
      option.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
      option.setPadding(0, 10, 0, 10);
      ((LinearLayout.LayoutParams)option.getLayoutParams()).setMargins(0, 15, 0, 15);
      option.setBackgroundResource(R.drawable.key_selector);
      TouchListener listener = new TouchListener(option, new Handler());
      option.setOnTouchListener(listener);
      option.setOnHoverListener(listener);
    }
  }

  boolean handleElement(int elem) {
    comboReceived.add(elem);
    if (comboReceived.equals(expectedCombo)) {
      Toast.makeText(this, "Passphrase correct", Toast.LENGTH_SHORT).show();
      comboReceived.clear();

      v.vibrate(new long[] {0, 300, 100, 600, 100, 600}, -1);
      clearAndAddRandomElements();

      locked = false;

      if (root_home) {
        InvokeStockLauncher();
      } else {
        finish();
      }

      return true;
    }
    if (comboReceived.size() == expectedCombo.size()) {
      Toast.makeText(this, "Passphrase incorrect", Toast.LENGTH_SHORT).show();
      comboReceived.clear();

      v.vibrate(new long[] {0, 300, 100, 200, 50, 200, 50, 200, 50, 200}, -1);
      clearAndAddRandomElements();
      return true;
    }
    return false;
  }

  ArrayList<Integer> comboReceived = new ArrayList<Integer>();
  static private final ArrayList<Integer> expectedCombo = new ArrayList<Integer>();
  static {
    expectedCombo.add(0);
    expectedCombo.add(2);
    expectedCombo.add(1);
    expectedCombo.add(4);
    expectedCombo.add(3);
  }

  private void InvokeStockLauncher() {
    Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
    intentToResolve.addCategory(Intent.CATEGORY_HOME);
    List<ResolveInfo> ri = getPackageManager().queryIntentActivities(intentToResolve, 0);
    ResolveInfo result = null;
    for (ResolveInfo info : ri) {
      if (info.activityInfo.applicationInfo.packageName == this.getClass().getPackage().getName()) {
        continue;
      }
      result = info;
      break;
    }
    if (result != null) {
      Intent intent = new Intent(intentToResolve);
      intent.setClassName(result.activityInfo.applicationInfo.packageName, result.activityInfo.name);
      intent.setAction(Intent.ACTION_MAIN);
      intent.addCategory(Intent.CATEGORY_HOME);
      intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
      startActivity(intent);
    } else {
      Toast.makeText(this, "Error finding launcher, reverting to default.", Toast.LENGTH_SHORT).show();
      getPackageManager().setComponentEnabledSetting(new ComponentName(this, UnlockActivity.class), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.addCategory(Intent.CATEGORY_HOME);
      startActivity(intent);
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);

    if (!hasFocus) {
      recentCloseHandler.postDelayed(recentCloserRunnable, 10);
    }
  }

  private void toggleRecents() {
      Intent closeRecents = new Intent("com.android.systemui.recent.action.TOGGLE_RECENTS");
      closeRecents.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
      ComponentName recents =
          new ComponentName("com.android.systemui",
                            "com.android.systemui.recent.RecentsActivity");
      closeRecents.setComponent(recents);
      this.startActivity(closeRecents);
  }

  private Handler recentCloseHandler = new Handler();
  private Runnable recentCloserRunnable = new Runnable() {
      @Override
      public void run() {
      ActivityManager am = (ActivityManager)getApplicationContext().getSystemService(
        Context.ACTIVITY_SERVICE);
      ComponentName cn = am.getRunningTasks(1).get(0).topActivity;

      if (cn != null &&
          cn.getClassName().equals("com.android.systemui.recent.RecentsActivity")) {
        toggleRecents();
      }
    }
  };

  private Vibrator v;
  private boolean elementActive = false;
  static private final int numItems = 5;
  protected static boolean locked = true;
  protected static boolean root_home = true;
}
