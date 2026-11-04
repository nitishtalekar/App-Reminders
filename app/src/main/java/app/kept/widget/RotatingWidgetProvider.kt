package app.kept.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import app.kept.app
import app.kept.notify.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class RotatingWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: android.content.Intent) {
        val forward = when (intent.action) {
            WidgetUpdater.ACTION_NEXT -> true
            WidgetUpdater.ACTION_PREV -> false
            else -> return super.onReceive(context, intent)
        }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) async(context) { WidgetUpdater.flip(context, id, forward) }
    }

    private fun async(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { block() } finally { pending.finish() }
        }
    }

    // Called when the widget is placed, after reboot, and every updatePeriodMillis (hourly):
    // a cheap, battery-safe way to keep "days left" correct even if every other trigger was missed.
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        async(context) { WidgetUpdater.refresh(context, ids) }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        async(context) { WidgetUpdater.refresh(context, intArrayOf(id)) }

    override fun onEnabled(context: Context) =
        // First widget placed: make sure the midnight alarm exists even if the app was never opened.
        async(context) { Scheduler.scheduleAll(context.app) }
}

/** The black / white / gray variant. Same behaviour; WidgetUpdater picks the palette from this class. */
class MonoWidgetProvider : RotatingWidgetProvider()
