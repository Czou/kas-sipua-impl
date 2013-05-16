package com.kurento.kas.sip.ua;

import javax.sip.ListeningPoint;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.kurento.kas.sip.R;

public class Preferences {

	enum Direction {
		SENDRECV, RECVONLY, SENDONLY, INACTIVE;
	}

	public static final String STUN_SERVER_ADDRESS = "STUN_SERVER_ADDRESS";
	public static final String STUN_SERVER_PORT = "STUN_SERVER_PORT";
	public static final String STUN_SERVER_PASSWORD = "STUN_SERVER_PASSWORD";

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

	// Media
	public static final String VIDEO_DIRECTION = "VIDEO_DIRECTION";
	public static final String AUDIO_DIRECTION = "AUDIO_DIRECTION";
	public static final String FRONT_CAMERA = "FRONT_CAMERA";

	private String stunServerAdress;
	private int stunServerPort;
	private String stunServerPassword;

	private boolean sipOnlyIpv4;
	private String sipTransport;

	private boolean enableSipKeepAlive;
	private int sipKeepAliveSeconds;

	private String sipProxyServerAddress;
	private int sipProxyServerPort;

	private int sipLocalPort;
	private int sipRegExpires;

	private Direction videoDirection;
	private Direction audioDirection;
	private boolean frontCamera;

	Preferences(Context context) throws KurentoSipException {
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);

		stunServerAdress = pref.getString(STUN_SERVER_ADDRESS, context
				.getString(R.string.preference_stun_server_address_default));

		stunServerPort = pref.getInt(STUN_SERVER_PORT, Integer.parseInt(context
				.getString(R.integer.preference_stun_server_port_default)));
		if (stunServerPort < 1024)
			throw new KurentoSipException(STUN_SERVER_PORT + " must be >= 1024");

		stunServerPassword = pref.getString(STUN_SERVER_PASSWORD, context
				.getString(R.string.preference_stun_server_password_default));

		String sipOnlyIpv4Str = pref.getString(SIP_ONLY_IPV4,
				context.getString(R.string.preference_sip_only_ipv4_default));
		if ("true".equalsIgnoreCase(sipOnlyIpv4Str))
			sipOnlyIpv4 = true;
		else if ("false".equalsIgnoreCase(sipOnlyIpv4Str))
			sipOnlyIpv4 = false;
		else
			throw new KurentoSipException(SIP_ONLY_IPV4
					+ " must be true or false.");

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

		String sipEnableSipKeepAliveDefault = pref
				.getString(
						ENABLE_SIP_KEEP_ALIVE,
						context.getString(R.string.preference_enable_sip_keep_alive_default));
		if ("true".equalsIgnoreCase(sipEnableSipKeepAliveDefault))
			enableSipKeepAlive = true;
		else if ("false".equalsIgnoreCase(sipEnableSipKeepAliveDefault))
			enableSipKeepAlive = false;
		else
			throw new KurentoSipException(ENABLE_SIP_KEEP_ALIVE
					+ " must be true or false.");

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
		if (sipProxyServerPort < 1024)
			throw new KurentoSipException(SIP_PROXY_SERVER_PORT
					+ " must be >= 1024. It is mandatory.");

		sipLocalPort = pref.getInt(SIP_LOCAL_PORT, Integer.parseInt(context
				.getString(R.integer.preference_sip_local_port_default)));
		if (sipLocalPort < 1024)
			throw new KurentoSipException(SIP_LOCAL_PORT + " must be > 1024");

		sipRegExpires = pref.getInt(SIP_REG_EXPIRES, Integer.parseInt(context
				.getString(R.integer.preference_sip_reg_expires_default)));
		if (sipRegExpires < 0)
			throw new KurentoSipException(SIP_REG_EXPIRES + " must be > 0");

		String videoDirectionStr = pref.getString(VIDEO_DIRECTION,
				context.getString(R.string.preference_video_direction_default));
		if (Direction.SENDRECV.toString().equalsIgnoreCase(videoDirectionStr))
			videoDirection = Direction.SENDRECV;
		else if (Direction.RECVONLY.toString().equalsIgnoreCase(
				videoDirectionStr))
			videoDirection = Direction.RECVONLY;
		else if (Direction.SENDONLY.toString().equalsIgnoreCase(
				videoDirectionStr))
			videoDirection = Direction.SENDONLY;
		else if (Direction.INACTIVE.toString().equalsIgnoreCase(
				videoDirectionStr))
			videoDirection = Direction.INACTIVE;
		else
			throw new KurentoSipException(VIDEO_DIRECTION
					+ " must be sendrecv, recvonly, sendonly or inactive.");

		String audioDirectionStr = pref.getString(AUDIO_DIRECTION,
				context.getString(R.string.preference_audio_direction_default));
		if (Direction.SENDRECV.toString().equalsIgnoreCase(audioDirectionStr))
			audioDirection = Direction.SENDRECV;
		else if (Direction.RECVONLY.toString().equalsIgnoreCase(
				audioDirectionStr))
			audioDirection = Direction.RECVONLY;
		else if (Direction.SENDONLY.toString().equalsIgnoreCase(
				audioDirectionStr))
			audioDirection = Direction.SENDONLY;
		else if (Direction.INACTIVE.toString().equalsIgnoreCase(
				audioDirectionStr))
			audioDirection = Direction.INACTIVE;
		else
			throw new KurentoSipException(VIDEO_DIRECTION
					+ " must be sendrecv, recvonly, sendonly or inactive.");

		String frontCameraStr = pref.getString(FRONT_CAMERA,
				context.getString(R.string.preference_front_camera_default));
		if ("true".equalsIgnoreCase(frontCameraStr))
			frontCamera = true;
		else if ("false".equalsIgnoreCase(frontCameraStr))
			frontCamera = false;
		else
			throw new KurentoSipException(FRONT_CAMERA
					+ " must be true or false.");
	}

	public String getStunServerAdress() {
		return stunServerAdress;
	}

	public int getStunServerPort() {
		return stunServerPort;
	}

	public String getStunServerPassword() {
		return stunServerPassword;
	}

	public boolean isSipOnlyIpv4() {
		return sipOnlyIpv4;
	}

	public String getSipTransport() {
		return sipTransport;
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

	public Direction getVideoDirection() {
		return videoDirection;
	}

	public Direction getAudioDirection() {
		return audioDirection;
	}

	public boolean isFrontCamera() {
		return frontCamera;
	}

}
