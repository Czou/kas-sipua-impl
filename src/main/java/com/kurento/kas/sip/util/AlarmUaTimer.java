package com.kurento.kas.sip.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

public class AlarmUaTimer {

	private final static Logger log = LoggerFactory
			.getLogger(AlarmUaTimer.class.getSimpleName());

	private final AlarmManager alarmManager;
	private final Context context;
	private final int type;

	public AlarmUaTimer(Context context, int type) {
		this.context = context;
		this.type = type;
		alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
	}

	public void cancel(KurentoUaTimerTask task) {
		Intent serviceIntent = new Intent(task.getId());
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
				serviceIntent, 0);
		alarmManager.cancel(pendingIntent);
		try {
			context.unregisterReceiver(task.getReceiver());
		} catch (Throwable t) {
			log.error("Error unregistering receiver: " + task.getId());
		}
	}

	public void schedule(final KurentoUaTimerTask task, long delay, long period) {
		Intent serviceIntent = new Intent(task.getId());

		PendingIntent pendingIntent = PendingIntent.getBroadcast(this.context,
				0, serviceIntent, 0);
		context.registerReceiver(task.getReceiver(),
				new IntentFilter(task.getId()));

		alarmManager.setRepeating(type, SystemClock.elapsedRealtime() + delay,
				period, pendingIntent);
	}

}
