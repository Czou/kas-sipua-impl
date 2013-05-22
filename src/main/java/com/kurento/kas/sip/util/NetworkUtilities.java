package com.kurento.kas.sip.util;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkUtilities {

	private static final Logger log = LoggerFactory
			.getLogger(NetworkUtilities.class.getSimpleName());

	/*
	 * Get the first reachable address matching parameter pattern
	 */
	public static InetAddress getLocalInterface(String pattern, boolean onlyIPv4)
			throws IOException {

		Enumeration<NetworkInterface> intfEnum = NetworkInterface
				.getNetworkInterfaces();

		while (intfEnum.hasMoreElements()) {
			NetworkInterface intf = intfEnum.nextElement();
			Enumeration<InetAddress> addrEnum = intf.getInetAddresses();
			while (addrEnum.hasMoreElements()) {
				InetAddress inetAddress = addrEnum.nextElement();
				log.debug("Found interface: IFNAME=" + intf.getDisplayName()
						+ "; ADDR=" + inetAddress.getHostAddress());

				// Check if only IPV4 is requested
				if (onlyIPv4 && !(inetAddress instanceof Inet4Address)) {
					continue;
				}

				// If address matches pattern return it. No matter what kind of
				// address it is
				if (pattern != null && !"".equals(pattern)) {

					if (intf.getDisplayName().equals(pattern)
							|| inetAddress.getHostAddress().equals(pattern)
							|| inetAddress.getHostAddress().equals(pattern)) {
						return inetAddress;
					}
				} else {
					// By default do not return multicast addresses (224...)
					if (inetAddress.isMulticastAddress()) {
						continue;
					}
					// By default do not return loopback addresses (127...)
					if (inetAddress.isLoopbackAddress()) {
						continue;
					}
					// By default do not return link local address (169...)
					if (inetAddress.isLinkLocalAddress()) {
						continue;
					}
					if (inetAddress.isReachable(30000)) {
						// Return only reachable interfaces
						return inetAddress;
					}
				}
			}
		}

		return null;
	}

}
