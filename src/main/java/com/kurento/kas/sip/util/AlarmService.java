package com.kurento.kas.sip.util;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class AlarmService extends Service {

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
				try {
					AlarmUaTimer.getTaskTable().get(uuid).run();
				} catch (Exception e) {
					// Do nothing
				}
			}
		}
	}

}
