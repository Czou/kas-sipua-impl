package com.kurento.kas.sip.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

public class AlarmUaTimer {

	private AlarmManager alarmManager;
	private Context context;

	private static HashMap<Integer, KurentoUaTimerTask> taskTable = new HashMap<Integer, KurentoUaTimerTask>();

	public AlarmUaTimer(Context context) {
		this.context = context;
		alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
	}

	public static HashMap<Integer, KurentoUaTimerTask> getTaskTable() {
		return taskTable;
	}

	public void cancel(KurentoUaTimerTask task) {
		Integer uuid = -1;
		for (Iterator<Integer> i = taskTable.keySet().iterator(); i.hasNext();) {
			Integer key = (Integer) i.next();
			if (taskTable.get(key).equals(task)) {
				uuid = key;
			}
		}
		Intent serviceIntent = new Intent();
		serviceIntent.setClass(context.getApplicationContext(),
				AlarmService.class);
		PendingIntent pendingIntent = PendingIntent.getService(context, uuid,
				serviceIntent, 0);
		alarmManager.cancel(pendingIntent);
		taskTable.remove(uuid);
	}

	public void schedule(final KurentoUaTimerTask task, long delay, long period) {
		if (!taskTable.containsValue(task)) {
			Integer uuid = new Random().nextInt();
			taskTable.put(uuid, task);

			Bundle extras = new Bundle();
			extras.putInt("uuid", uuid);

			Intent serviceIntent = new Intent();
			serviceIntent.setClass(context.getApplicationContext(),
					AlarmService.class);
			serviceIntent.putExtras(extras);

			PendingIntent pendingIntent = PendingIntent.getService(
					this.context, uuid, serviceIntent, 0);

			alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime() + delay, period,
					pendingIntent);
		}
	}

}
