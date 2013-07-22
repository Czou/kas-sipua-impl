package com.kurento.kas.sip.util;

import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Handler;
import android.os.Looper;

public class LooperThread extends Thread {

	private static final Logger log = LoggerFactory
			.getLogger(LooperThread.class.getSimpleName());

	private Handler mHandler;
	private final Semaphore sem = new Semaphore(0);

	@Override
	public void run() {
		Looper.prepare();
		mHandler = new Handler();
		sem.release();
		Looper.loop();
	}

	public boolean post(Runnable r) {
		try {
			sem.acquire();
			boolean ret = mHandler.post(r);
			sem.release();

			return ret;
		} catch (InterruptedException e) {
			log.error("Cannot run", e);
			return false;
		}
	}

	public void quit() {
		mHandler.getLooper().quit();
	}
}
