package org.hoteldumbria;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;

public class Utilities {

    /*  Fuente
       https://stackoverflow.com/questions/1819142/how-should-i-validate-an-e-mail-address
    */
    public static boolean isValidEmail(CharSequence target) {
        return !TextUtils.isEmpty(target) && android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR_MR1)
    public boolean isPhoneAwakeOrInSleepMode(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isScreenAwake = (Build.VERSION.SDK_INT < 20 ? powerManager.isScreenOn() : powerManager.isInteractive());
        return isScreenAwake;
    }

    public boolean isPhoneLocked(Context context) {
        boolean locked;
        KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            //it is locked
            locked = true;
        } else {
            //it is not locked
            locked = false;
        }
        return locked;
    }

}
