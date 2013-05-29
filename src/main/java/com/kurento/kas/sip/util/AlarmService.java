package com.kurento.kas.sip.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class AlarmService extends Service {

	private static final Logger log = LoggerFactory
			.getLogger(AlarmService.class.getSimpleName());

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		if (intent != null) {
			Bundle b = intent.getExtras();

			if (b != null) {
				Integer uuid = b.getInt("uuid");
				KurentoUaTimerTask task = AlarmUaTimer.getTaskTable().get(uuid);
				if (task != null)
					task.run();
				else
					log.error("No task with uuid " + uuid);
			}
		}
	}

}
