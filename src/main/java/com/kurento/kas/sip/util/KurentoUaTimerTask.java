package com.kurento.kas.sip.util;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public abstract class KurentoUaTimerTask {

	private final static Logger log = LoggerFactory
			.getLogger(KurentoUaTimerTask.class.getSimpleName());

	private final String uuid;
	private final BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			log.trace("Running task: " + getId());
			run();
		}

	};

	public KurentoUaTimerTask() {
		uuid = UUID.randomUUID().toString();
	}

	public String getId() {
		return uuid;
	}

	BroadcastReceiver getReceiver() {
		return receiver;
	}

	protected abstract void run();

}
