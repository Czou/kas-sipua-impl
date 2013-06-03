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

import javax.sip.ServerTransaction;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipUA;
import com.kurento.kas.ua.KurentoException;
import com.kurento.kas.ua.impl.BaseCall.CreateSdpAnswerObserver;

public class SInvite extends STransaction {

	private static final Logger log = LoggerFactory.getLogger(SInvite.class
			.getSimpleName());

	public SInvite(SipUA sipUA, ServerTransaction serverTransaction)
			throws KurentoSipException {
		super(sipUA, serverTransaction);

		// Process request
		Request request = serverTransaction.getRequest();
		if (Request.INVITE.equals(method)) {
			log.debug("Process INVITE request");
			processInvite(request);
		} else if (Request.ACK.equals(method)) {
			log.debug("Process ACK resquest");
			processAck();
		} else {
			log.error("Bad request. Method not allowed in a Server INVITE: "
					+ request.getMethod());
			sendResponse(Response.BAD_REQUEST, null);
		}
	}

	private void processInvite(Request request) throws KurentoSipException {
		sendResponse(Response.RINGING, null);

		// Process INVITE request
		if (getContentLength(request) == 0) {
			// TODO Support INVITE request with no offer ==> negotiation takes
			// place between response and ACK

		} else {
			// INVITE with SDP. request for process
			log.debug("Process offer...");

			String sdpOffer = new String(request.getRawContent());
			CreateSdpAnswerObserver o = new CreateSdpAnswerObserver() {

				@Override
				public void onSdpAnswerCreated(String sdp) {
					call.removeCreateSdpAnswerObserver(this);
					SInvite.this.call.incomingCall(SInvite.this);
				}

				@Override
				public void onError(KurentoException exception) {
					call.removeCreateSdpAnswerObserver(this);
					try {
						SInvite.this.sendResponse(
								Response.UNSUPPORTED_MEDIA_TYPE, null);
					} catch (KurentoSipException e) {
						log.error("Unable to send response", e);
					}
				}
			};
			call.addCreateSdpAnswerObserver(o);
			call.createSdpAnswer(sdpOffer, o);
		}

	}

	private void processAck() {
		log.debug("Invite transaction received a valid ACK for non 2xx response");
	}
}
