package com.kurento.kas.sip.ua;

import javax.sip.ListeningPoint;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.kurento.kas.sip.R;

public class Preferences extends com.kurento.kas.media.impl.Preferences {

	// SIP Connection
	public static final String SIP_ONLY_IPV4 = "SIP_ONLY_IPV4";
	public static final String SIP_TRANSPORT = "TRANSPORT";

	public static final String ENABLE_SIP_KEEP_ALIVE = "ENABLE_SIP_KEEP_ALIVE";
	public static final String SIP_KEEP_ALIVE_SECONDS = "SIP_KEEP_ALIVE_SECONDS";

	// SIP Proxy Server
	public static final String SIP_PROXY_SERVER_ADDRESS = "PROXY_SERVER_ADDRESS"; // Mandatory
	public static final String SIP_PROXY_SERVER_PORT = "PROXY_SERVER_PORT"; // Mandatory

	// Local SIP stack
	public static final String SIP_LOCAL_PORT = "LOCAL_PORT";
	public static final String SIP_REG_EXPIRES = "REG_EXPIRES";

	private final boolean sipOnlyIpv4;
	private String sipTransport;

	private final boolean enableSipKeepAlive;
	private final int sipKeepAliveSeconds;

	private final String sipProxyServerAddress;
	private final int sipProxyServerPort;

	private final int sipLocalPort;
	private final int sipRegExpires;

	protected Preferences(Context context) throws KurentoSipException {
		super(context);

		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);

		sipOnlyIpv4 = pref.getBoolean(SIP_ONLY_IPV4, context.getResources()
				.getBoolean(R.bool.preference_sip_only_ipv4_default));

		sipTransport = pref.getString(SIP_TRANSPORT,
				context.getString(R.string.preference_sip_transport_default));
		if (ListeningPoint.UDP.equalsIgnoreCase(sipTransport))
			sipTransport = ListeningPoint.UDP;
		else if (ListeningPoint.TCP.equalsIgnoreCase(sipTransport))
			sipTransport = ListeningPoint.TCP;
		else if (ListeningPoint.TLS.equalsIgnoreCase(sipTransport))
			sipTransport = ListeningPoint.TLS;
		else
			throw new KurentoSipException(SIP_TRANSPORT
					+ " must be UDP, TCP or TLS.");

		enableSipKeepAlive = pref.getBoolean(
				ENABLE_SIP_KEEP_ALIVE,
				context.getResources().getBoolean(
						R.bool.preference_enable_sip_keep_alive_default));

		sipKeepAliveSeconds = pref
				.getInt(SIP_KEEP_ALIVE_SECONDS,
						Integer.parseInt(context
								.getString(R.integer.preference_sip_keep_alive_seconds_default)));
		if (sipKeepAliveSeconds < 0)
			throw new KurentoSipException(SIP_KEEP_ALIVE_SECONDS
					+ " must be >= 0");

		sipProxyServerAddress = pref
				.getString(
						SIP_PROXY_SERVER_ADDRESS,
						context.getString(R.string.preference_sip_proxy_server_address_default));
		if (sipProxyServerAddress == null || sipProxyServerAddress.equals(""))
			throw new KurentoSipException(SIP_PROXY_SERVER_ADDRESS
					+ " not assigned. It is mandatory.");

		sipProxyServerPort = pref
				.getInt(SIP_PROXY_SERVER_PORT,
						Integer.parseInt(context
								.getString(R.integer.preference_sip_proxy_server_port_default)));

		sipLocalPort = pref.getInt(SIP_LOCAL_PORT, Integer.parseInt(context
				.getString(R.integer.preference_sip_local_port_default)));
		if (sipLocalPort < 1024)
			throw new KurentoSipException(SIP_LOCAL_PORT + " must be > 1024");

		sipRegExpires = pref.getInt(SIP_REG_EXPIRES, Integer.parseInt(context
				.getString(R.integer.preference_sip_reg_expires_default)));
		if (sipRegExpires < 0)
			throw new KurentoSipException(SIP_REG_EXPIRES + " must be > 0");
	}

	public boolean isSipOnlyIpv4() {
		return sipOnlyIpv4;
	}

	public String getSipTransport() {
		return sipTransport;
	}

	public boolean isPersistentConnection() {
		// TODO: add preference to enable or disable persistent connection
		return ListeningPoint.TCP.equalsIgnoreCase(sipTransport)
				|| ListeningPoint.TLS.equalsIgnoreCase(sipTransport);
	}

	public boolean isEnableSipKeepAlive() {
		return enableSipKeepAlive;
	}

	public int getSipKeepAliveSeconds() {
		return sipKeepAliveSeconds;
	}

	public String getSipProxyServerAddress() {
		return sipProxyServerAddress;
	}

	public int getSipProxyServerPort() {
		return sipProxyServerPort;
	}

	public int getSipLocalPort() {
		return sipLocalPort;
	}

	public int getSipRegExpires() {
		return sipRegExpires;
	}

}
