package com.kurento.kas.sip.ua;

import javax.sip.ListeningPoint;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.kurento.kas.sip.R;

public class Preferences {

	public static final String SIP_ONLY_IPV4 = "SIP_ONLY_IPV4";
	public static final String SIP_TRANSPORT = "TRANSPORT";

	public static final String SIP_PROXY_SERVER_ADDRESS = "PROXY_SERVER_ADDRESS"; // Mandatory
	public static final String SIP_PROXY_SERVER_PORT = "PROXY_SERVER_PORT"; // Mandatory
	public static final String SIP_LOCAL_PORT = "LOCAL_PORT";

	public static final String SIP_REG_EXPIRES = "REG_EXPIRES";

	private boolean onlyIpv4 = true;
	private String transport = ListeningPoint.UDP;

	private String proxyServerAddress;
	private int proxyServerPort;
	private int localPort = 6060;

	private int regExpires;

	Preferences(Context context) throws KurentoSipException {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);

		proxyServerAddress = pref
				.getString(
						SIP_PROXY_SERVER_ADDRESS,
						context.getString(R.string.preference_sip_proxy_server_address_default));
		if (proxyServerAddress == null || proxyServerAddress.equals(""))
			throw new KurentoSipException(
					"PROXY_SERVER_ADDRESS not assigned. It is mandatory.");

		proxyServerPort = pref
				.getInt(SIP_PROXY_SERVER_PORT,
						Integer.parseInt(context
								.getString(R.integer.preference_sip_proxy_server_port_default)));
		if (proxyServerPort < 1024)
			throw new KurentoSipException(
					"PROXY_SERVER_PORT must be >= 1024. It is mandatory.");

		regExpires = pref.getInt(SIP_REG_EXPIRES, Integer.parseInt(context
				.getString(R.integer.preference_sip_reg_expires_default)));
		if (regExpires < 0)
			throw new KurentoSipException("REG_EXPIRES must be > 0");

		// TODO: complete preferences
	}

	public boolean isOnlyIpv4() {
		return onlyIpv4;
	}

	public String getTransport() {
		return transport;
	}

	public String getProxyServerAddress() {
		return proxyServerAddress;
	}

	public int getProxyServerPort() {
		return proxyServerPort;
	}

	public int getLocalPort() {
		return localPort;
	}

	public int getRegExpires() {
		return regExpires;
	}

}
