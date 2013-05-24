/*
Kurento Sip User Agent implementation.
Copyright (C) <2011>  <Tikal Technologies>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License version 3
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.kurento.kas.sip.ua;

import gov.nist.core.net.NetworkLayer;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;

/**
 * SslNetworkLayer implementation for Android using keystores/truststores from
 * res dir.
 * 
 */
public class KurentoSslNetworkLayer implements NetworkLayer {

	private static final Logger log = LoggerFactory
			.getLogger(KurentoSslNetworkLayer.class.getSimpleName());

	private SSLSocketFactory sslSocketFactory;

	private SSLServerSocketFactory sslServerSocketFactory;

	public KurentoSslNetworkLayer() throws GeneralSecurityException {
		SecureRandom secureRandom = new SecureRandom();
		secureRandom.nextInt();
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustAllCerts, secureRandom);
		sslServerSocketFactory = sslContext.getServerSocketFactory();
		sslSocketFactory = sslContext.getSocketFactory();
	}

	public KurentoSslNetworkLayer(Context context, String trustStoreRawResname,
			String trustStorePassword) throws GeneralSecurityException,
			IOException {

		SecureRandom secureRandom = new SecureRandom();
		secureRandom.nextInt();
		SSLContext sslContext = SSLContext.getInstance("TLS");

		String algorithm = KeyManagerFactory.getDefaultAlgorithm();

		// TrustStore
		TrustManagerFactory tmFactory = TrustManagerFactory
				.getInstance(algorithm);
		KeyStore trustStore = KeyStore.getInstance("BKS");

		InputStream trustStoreStream = context.getResources().openRawResource(
				context.getResources().getIdentifier(trustStoreRawResname,
						"raw", context.getPackageName()));

		trustStore.load(trustStoreStream, trustStorePassword.toCharArray());
		tmFactory.init(trustStore);

		// TODO: complete when it will be needed if the server requires client
		// certificate
		// KeyStore
		// KeyManagerFactory kmFactory =
		// KeyManagerFactory.getInstance(algorithm);
		// KeyStore keyStore = KeyStore.getInstance("BKS");
		// InputStream keyStoreStream = context.getResources().openRawResource(
		// R.raw.keystore);
		// keyStore.load(keyStoreStream, keyStorePassword.toCharArray());
		// kmFactory.init(keyStore, keyStorePassword.toCharArray());

		sslContext.init(null, tmFactory.getTrustManagers(), secureRandom);
		// sslContext.init(kmFactory.getKeyManagers(),
		// tmFactory.getTrustManagers(), secureRandom);
		sslServerSocketFactory = sslContext.getServerSocketFactory();
		sslSocketFactory = sslContext.getSocketFactory();
	}

	public ServerSocket createServerSocket(int port, int backlog,
			InetAddress bindAddress) throws IOException {
		return new ServerSocket(port, backlog, bindAddress);
	}

	public Socket createSocket(InetAddress address, int port)
			throws IOException {
		return new Socket(address, port);
	}

	public DatagramSocket createDatagramSocket() throws SocketException {
		return new DatagramSocket();
	}

	public DatagramSocket createDatagramSocket(int port, InetAddress laddr)
			throws SocketException {
		return new DatagramSocket(port, laddr);
	}

	public SSLServerSocket createSSLServerSocket(int port, int backlog,
			InetAddress bindAddress) throws IOException {
		return (SSLServerSocket) sslServerSocketFactory.createServerSocket(
				port, backlog, bindAddress);
	}

	public SSLSocket createSSLSocket(InetAddress address, int port)
			throws IOException {
		return (SSLSocket) sslSocketFactory.createSocket(address, port);
	}

	public SSLSocket createSSLSocket(InetAddress address, int port,
			InetAddress myAddress) throws IOException {
		return (SSLSocket) sslSocketFactory.createSocket(address, port,
				myAddress, 0);
	}

	public Socket createSocket(InetAddress address, int port,
			InetAddress myAddress) throws IOException {
		if (myAddress != null)
			return new Socket(address, port, myAddress, 0);
		else
			return new Socket(address, port);
	}

	/**
	 * Creates a new Socket, binds it to myAddress:myPort and connects it to
	 * address:port.
	 * 
	 * @param address
	 *            the InetAddress that we'd like to connect to.
	 * @param port
	 *            the port that we'd like to connect to
	 * @param myAddress
	 *            the address that we are supposed to bind on or null for the
	 *            "any" address.
	 * @param myPort
	 *            the port that we are supposed to bind on or 0 for a random
	 *            one.
	 * 
	 * @return a new Socket, bound on myAddress:myPort and connected to
	 *         address:port.
	 * @throws IOException
	 *             if binding or connecting the socket fail for a reason
	 *             (exception relayed from the correspoonding Socket methods)
	 */
	public Socket createSocket(InetAddress address, int port,
			InetAddress myAddress, int myPort) throws IOException {
		if (myAddress != null)
			return new Socket(address, port, myAddress, myPort);
		else if (port != 0) {
			// myAddress is null (i.e. any) but we have a port number
			Socket sock = new Socket();
			sock.bind(new InetSocketAddress(port));
			sock.connect(new InetSocketAddress(address, port));
			return sock;
		} else
			return new Socket(address, port);
	}

	// Trust manager that does not validate certificate chains
	private static final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
			// Nothing to do
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
			// Nothing to do
		}
	} };

}
