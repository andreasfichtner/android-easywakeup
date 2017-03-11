package android_easywakeup.retterdesapok.de.easywakeup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static android_easywakeup.retterdesapok.de.easywakeup.BackgroundService.SCREEN_ON_FLAG;

/**
 * Created by Andreas on 09.03.2017.
 */

public class BroadcastHandler extends BroadcastReceiver
{
    public void onReceive(Context context, Intent incomingIntent)
    {
        if (incomingIntent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            Intent intent = new Intent(context, BackgroundService.class);
            intent.putExtra(SCREEN_ON_FLAG, true);
            context.startService(intent);
        } else {
            Intent intent = new Intent(context, BackgroundService.class);
            intent.putExtra(SCREEN_ON_FLAG, false);
            context.startService(intent);
        }
    }
}