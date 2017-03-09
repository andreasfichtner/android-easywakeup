package android_easywakeup.retterdesapok.de.easywakeup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Andreas on 09.03.2017.
 */

public class Autostart extends BroadcastReceiver
{
    public void onReceive(Context context, Intent arg1)
    {
        Intent intent = new Intent(context, BackgroundService.class);
        context.startService(intent);
    }
}