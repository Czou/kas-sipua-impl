package com.kurento.kas.sip.ua;

import javax.sip.ListeningPoint;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.kurento.kas.sip.R;

public class Preferences {

	// SIP Connection
	public static final String SIP_ONLY_IPV4 = "SIP_ONLY_IPV4";
	public static final String SIP_TRANSPORT = "TRANSPORT";
	public static final String SIP_PERSISTENT_CONNECTION = "SIP_PERSISTENT_CONNECTION";

	public static final String ENABLE_SIP_KEEP_ALIVE = "ENABLE_SIP_KEEP_ALIVE";
	public static final String SIP_KEEP_ALIVE_SECONDS = "SIP_KEEP_ALIVE_SECONDS";

	public static final String SIP_TRUST_ANY_TLS_CONNECTION = "SIP_TRUST_ANY_TLS_CONNECTION";
	public static final String SIP_TLS_TRUSTSTORE_RAW_RES_NAME = "SIP_TLS_TRUSTSTORE_RAW_RES_NAME";
	public static final String SIP_TLS_TRUSTSTORE_PASSWORD = "SIP_TLS_TRUSTSTORE_PASSWORD";

	// SIP Proxy Server
	public static final String SIP_PROXY_SERVER_ADDRESS = "PROXY_SERVER_ADDRESS"; // Mandatory
	public static final String SIP_PROXY_SERVER_PORT = "PROXY_SERVER_PORT"; // Mandatory

	// Local SIP stack
	public static final String SIP_LOCAL_PORT = "LOCAL_PORT";
	public static final String SIP_REG_EXPIRES = "REG_EXPIRES";

	private final Context context;
	private final SharedPreferences pref;

	protected Preferences(Context context) {
		this.context = context;
		pref = PreferenceManager.getDefaultSharedPreferences(context);
	}

	public boolean isSipOnlyIpv4() {
		return pref.getBoolean(SIP_ONLY_IPV4, context.getResources()
				.getBoolean(R.bool.preference_sip_only_ipv4_default));
	}

	public String getSipTransport() {
		String sipTransport = pref.getString(SIP_TRANSPORT,
				context.getString(R.string.preference_sip_transport_default));
		if (ListeningPoint.UDP.equalsIgnoreCase(sipTransport))
			sipTransport = ListeningPoint.UDP;
		else if (ListeningPoint.TCP.equalsIgnoreCase(sipTransport))
			sipTransport = ListeningPoint.TCP;
		else if (ListeningPoint.TLS.equalsIgnoreCase(sipTransport))
			sipTransport = ListeningPoint.TLS;
		else
			throw new RuntimeException(SIP_TRANSPORT
					+ " must be UDP, TCP or TLS.");

		return sipTransport;
	}

	public boolean isPersistentConnection() {
		String sipTransport = getSipTransport();
		boolean persistentConnection = pref.getBoolean(
				SIP_PERSISTENT_CONNECTION,
				context.getResources().getBoolean(
						R.bool.preference_sip_persistent_connection_default));

		return persistentConnection
				&& (ListeningPoint.TCP.equalsIgnoreCase(sipTransport) || ListeningPoint.TLS
						.equalsIgnoreCase(sipTransport));
	}

	public boolean isEnableSipKeepAlive() {
		return pref.getBoolean(ENABLE_SIP_KEEP_ALIVE, context.getResources()
				.getBoolean(R.bool.preference_enable_sip_keep_alive_default));
	}

	public int getSipKeepAliveSeconds() {
		int sipKeepAliveSeconds = pref
				.getInt(SIP_KEEP_ALIVE_SECONDS,
						Integer.parseInt(context
								.getString(R.integer.preference_sip_keep_alive_seconds_default)));
		if (sipKeepAliveSeconds < 0)
			throw new RuntimeException(SIP_KEEP_ALIVE_SECONDS + " must be >= 0");

		return sipKeepAliveSeconds;
	}

	public boolean isSipTrustAnyTlsConnection() {
		return pref.getBoolean(
				SIP_TRUST_ANY_TLS_CONNECTION,
				context.getResources().getBoolean(
						R.bool.preference_sip_trust_any_tls_connection));
	}

	public String getSipTlsTruststoreRawResName() {
		return pref
				.getString(
						SIP_TLS_TRUSTSTORE_RAW_RES_NAME,
						context.getString(R.string.preference_sip_tls_truststore_raw_res_name));
	}

	public String getSipTlsTruststorePassword() {
		return pref.getString(SIP_TLS_TRUSTSTORE_PASSWORD, context
				.getString(R.string.preference_sip_tls_truststore_password));
	}

	public String getSipProxyServerAddress() {
		String sipProxyServerAddress = pref
				.getString(
						SIP_PROXY_SERVER_ADDRESS,
						context.getString(R.string.preference_sip_proxy_server_address_default));
		if (sipProxyServerAddress == null || sipProxyServerAddress.equals(""))
			throw new RuntimeException(SIP_PROXY_SERVER_ADDRESS
					+ " not assigned. It is mandatory.");

		return sipProxyServerAddress;
	}

	public int getSipProxyServerPort() {
		return pref
				.getInt(SIP_PROXY_SERVER_PORT,
						Integer.parseInt(context
								.getString(R.integer.preference_sip_proxy_server_port_default)));
	}

	public int getSipLocalPort() {
		int sipLocalPort = pref.getInt(SIP_LOCAL_PORT, Integer.parseInt(context
				.getString(R.integer.preference_sip_local_port_default)));
		if (sipLocalPort < 1024)
			throw new RuntimeException(SIP_LOCAL_PORT + " must be >= 1024");

		return sipLocalPort;
	}

	public int getSipRegExpires() {
		int sipRegExpires = pref
				.getInt(SIP_REG_EXPIRES,
						Integer.parseInt(context
								.getString(R.integer.preference_sip_reg_expires_default)));
		if (sipRegExpires < 0)
			throw new RuntimeException(SIP_REG_EXPIRES + " must be > 0");

		return sipRegExpires;
	}

}
