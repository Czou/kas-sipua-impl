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
package com.kurento.kas.sip.transaction;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Locale;

import javax.sip.InvalidArgumentException;
import javax.sip.ResponseEvent;
import javax.sip.address.URI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipRegister;
import com.kurento.kas.sip.ua.SipUA;
import com.kurento.kas.ua.KurentoException;
import com.kurento.kas.ua.Register;

public class CRegister extends CTransaction {

	private static final Logger log = LoggerFactory.getLogger(CRegister.class
			.getSimpleName());

	SipRegister sipRegister;
	Register register;
	int expires;

	public CRegister(SipUA sipUA, SipRegister sipRegister, int expires)
			throws KurentoException, KurentoSipException {
		super(Request.REGISTER, sipUA, sipRegister.getRegister().getUri(),
				sipRegister.getRegister().getUri(), sipRegister.getCseq());

		this.sipRegister = sipRegister;
		this.register = sipRegister.getRegister();
		this.expires = expires;

		try {
			// REGISTER send special request URI: RFC3261 , 10.2
			String requestUri = "sip:" + register.getRealm();
			request.setRequestURI(sipUA.getAddressFactory().createURI(
					requestUri));

			// Set REGISTER CallId according to RFC3261 , 10.2 - CSeq
			CallIdHeader registrarCallId = sipUA.getSipProvider()
					.getNewCallId();
			registrarCallId.setCallId(sipRegister.getRegisterCallId());

			request.setHeader(registrarCallId);

			// Add specific REGISTER headers RFC3261, 10.2 - RequestURI
			request.addHeader(sipUA.getHeaderFactory().createExpiresHeader(
					expires));
		} catch (ParseException e) {
			throw new KurentoException("Unable to build REGISTER request", e);
		} catch (InvalidArgumentException e) {
			throw new KurentoSipException(
					"Invalid argument building expires header", e);
		}
	}

	@Override
	public void processResponse(ResponseEvent event) {
		Response response = event.getResponse();
		int statusCode = response.getStatusCode();

		if (statusCode == Response.OK) {
			log.info("<<<<<<< 200 OK: Register sucessful for user: "
					+ register.getUri());
			if (expires > 0) {
				long period = (long) (expires * 1000 * 0.5);
				log.debug("Period = " + expires);
				sipUA.getWakeupTimer().schedule(
						sipRegister.getSipRegisterTimerTask(), period, period);
				sipUA.getRegisterHandler().onUserOnline(register);
			} else {
				sipUA.getRegisterHandler().onUserOffline(register);
			}

		} else if (statusCode == Response.UNAUTHORIZED
				|| statusCode == Response.PROXY_AUTHENTICATION_REQUIRED) {
			// Peer Authentication
			try {
				sendWithAuth(event);
			} catch (KurentoSipException e) {
				String msg = "Unable to send Auth REGISTER";
				log.error(msg, e);
				sipUA.getRegisterHandler().onRegisterError(register,
						new KurentoException(e));
			}
		} else if (statusCode == Response.REQUEST_TIMEOUT) {
			// 408: Request TimeOut
			log.warn("<<<<<<< 408 REQUEST_TIMEOUT: Register Failure. Unable to contact registrar from "
					+ register.getUri());
			sipUA.getRegisterHandler().onUserOffline(register);
			// TODO: retry
		} else if (statusCode == Response.NOT_FOUND) { // 404: Not Found
			log.warn("<<<<<<< 404 NOT_FOUND: Register Failure. User "
					+ register.getUri() + " not found");
			sipUA.getRegisterHandler().onRegisterError(register,
					new KurentoException("404 NOT_FOUND"));
		} else if (statusCode == Response.FORBIDDEN) { // Forbidden
			log.warn("<<<<<<< 403 FORBIDDEN: Register Failure. User "
					+ register.getUri() + " forbiden");
			sipUA.getRegisterHandler().onAuthenticationFailure(register);
		} else if (statusCode == Response.SERVER_INTERNAL_ERROR) { // server
																	// errors
			log.warn("<<<<<<< 500 SERVER_INTERNAL_ERROR Register: Server Error: "
					+ response.getStatusCode());
			sipUA.getRegisterHandler().onRegisterError(register,
					new KurentoException("500 SERVER_INTERNAL_ERROR"));
		} else if (statusCode == Response.SERVICE_UNAVAILABLE) { // server
																	// errors
			log.warn("<<<<<<< 503 SERVICE_UNAVAILABLE Register: Service unavailable: "
					+ response.getStatusCode());
			sipUA.getRegisterHandler().onRegisterError(register,
					new KurentoException("503 SERVICE_UNAVAILABLE"));
		} else { // Non supported response code Discard
			log.warn("Register Failure. Status code: "
					+ response.getStatusCode());
			sipUA.getRegisterHandler().onRegisterError(register,
					new KurentoException("Register failure"));
		}
	}

	@Override
	public void processTimeout() {
		log.warn("Register request timeout for uri: " + register.getUri());
		sipUA.getRegisterHandler().onUserOffline(register);
		// TODO: retry
	}

	private void sendWithAuth(ResponseEvent event) throws KurentoSipException {
		Response response = event.getResponse();
		int statusCode = response.getStatusCode();

		createRequest();

		if (statusCode == 401 || statusCode == 407) { // 401 Peer Authentication
			log.info("Authentication Required in REGISTER transaction for user: "
					+ register.getUri());
			AuthorizationHeader authorization = getAuthorizationHearder(response);
			request.setHeader(authorization);
		} else if (statusCode == 407) { // 407: Proxy Auth Required
			log.info("Proxy Authentication Required in REGISTER transaction for user: "
					+ register.getUri());
			ProxyAuthenticateHeader proxyAuthenticateHeader;
			proxyAuthenticateHeader = (ProxyAuthenticateHeader) response
					.getHeader(ProxyAuthenticateHeader.NAME);
			log.debug("ProxyAuthenticateHeader is "
					+ proxyAuthenticateHeader.toString());
			request.setHeader(proxyAuthenticateHeader);
		}

		sendRequest(); // 2nd request with authentication
	}

	// AuthenticationHearder RFC2617 construction for request digest without qop
	// parameter and MD5 hash algorithm.
	private AuthorizationHeader getAuthorizationHearder(Response response)
			throws KurentoSipException {
		WWWAuthenticateHeader wwwAuthenticateHeader;
		AuthorizationHeader authorization = null;
		try {
			wwwAuthenticateHeader = (WWWAuthenticateHeader) response
					.getHeader(WWWAuthenticateHeader.NAME);

			String schema = wwwAuthenticateHeader.getScheme();
			String realm = wwwAuthenticateHeader.getRealm();
			String nonce = wwwAuthenticateHeader.getNonce();
			String alg = wwwAuthenticateHeader.getAlgorithm();
			String opaque = wwwAuthenticateHeader.getOpaque();

			log.debug("WWWAuthenticateHeader is "
					+ wwwAuthenticateHeader.toString());

			URI localUri = sipUA.getAddressFactory().createURI(
					register.getUri());

			HeaderFactory factory = sipUA.getHeaderFactory();
			authorization = factory.createAuthorizationHeader(schema);
			authorization.setUsername(register.getAuthuser());
			authorization.setRealm(realm);
			authorization.setNonce(nonce);
			authorization.setURI(localUri);
			String respon = getAuthResponse(register.getAuthuser(),
					register.getPassword(), realm, this.method,
					register.getUri(), nonce, alg);
			authorization.setResponse(respon);
			authorization.setAlgorithm(alg);
			authorization.setOpaque(opaque);
		} catch (ParseException e) {
			throw new KurentoSipException(
					"Error generating authentication hearder", e);
		} catch (NoSuchAlgorithmException e) {
			throw new KurentoSipException(
					"Error generating authentication hearder", e);
		}

		return authorization;
	}

	private String getAuthResponse(String userName, String password,
			String realm, String method, String uri, String nonce,
			String algorithm) throws NoSuchAlgorithmException {
		MessageDigest messageDigest = MessageDigest.getInstance(algorithm);

		// A1
		String A1 = userName + ":" + realm + ":" + password;
		byte mdbytes[] = messageDigest.digest(A1.getBytes());
		String HA1 = toHexString(mdbytes);
		log.debug("DigestClientAuthenticationMethod for HA1:" + HA1 + "!");

		// A2
		String A2 = method.toUpperCase(Locale.getDefault()) + ":" + uri;
		mdbytes = messageDigest.digest(A2.getBytes());
		String HA2 = toHexString(mdbytes);
		log.debug("DigestClientAuthenticationMethod for HA2:" + HA2 + "!");

		String KD = HA1 + ":" + nonce + ":" + HA2;
		mdbytes = messageDigest.digest(KD.getBytes());
		String response = toHexString(mdbytes);

		log.debug("DigestClientAlgorithm, response generated: " + response);

		return response;
	}

	private static String toHexString(byte b[]) {
		int pos = 0;
		char[] c = new char[b.length * 2];
		for (int i = 0; i < b.length; i++) {
			c[pos++] = toHex[(b[i] >> 4) & 0x0F];
			c[pos++] = toHex[b[i] & 0x0f];
		}
		return new String(c);
	}

	private static final char[] toHex = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

}
