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

import java.text.ParseException;

import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipCall;
import com.kurento.kas.sip.ua.SipUA;
import com.kurento.kas.ua.KurentoException;
import com.kurento.kas.ua.impl.BaseCall.CreateSdpOfferObserver;

public class CInvite extends CTransaction {

	private final static Logger log = LoggerFactory.getLogger(CInvite.class
			.getSimpleName());

	public CInvite(SipUA sipUA, SipCall call) throws KurentoSipException {
		super(Request.INVITE, sipUA, call);

		// INVITE REQUIRES TO INCREASE SEQUENCE NUMBER
		CTransaction.cSeqNumber++;
		log.debug("CTransaction.cSeqNumber: " + CTransaction.cSeqNumber);

		// CInvite.this.sendRequest();
		// CInvite.this.call.outgoingCall(CInvite.this);

		// Add special headers for INVITE
		// In the android implementation the contact header is added
		// automatically and adding it again causes an error
		// request.addHeader(buildContactHeader()); // Contact
		request.addHeader(buildAllowHeader()); // Allow
		request.addHeader(buildSupportedHeader()); // SupportHeader

		log.debug("Creating offer...");
		CreateSdpOfferObserver o = new CreateSdpOfferObserver() {
			@Override
			public void onSdpOfferCreated(String sdp) {
				CInvite.this.call.removeCreateSdpOfferObserver(this);
				try {
					log.info("onSdpOfferCreated SDP: " + sdp);
					CInvite.this.sendRequest(sdp);
					CInvite.this.call.outgoingCall(CInvite.this);
				} catch (KurentoSipException e) {
					CInvite.this.sipUA.getErrorHandler().onCallError(
							CInvite.this.call, new KurentoException(e));
				}
			}

			@Override
			public void onError(KurentoException exception) {
				CInvite.this.call.removeCreateSdpOfferObserver(this);
				CInvite.this.sipUA.getErrorHandler().onCallError(
						CInvite.this.call, new KurentoException(exception));
			}
		};
		call.addCreateSdpOfferObserver(o);
		call.createSdpOffer(o);
	}

	@Override
	public void processResponse(ResponseEvent event) {
		Response response = event.getResponse();
		int statusCode = response.getStatusCode();
		log.info("processResponse: " + statusCode + " dialog: " + this.dialog
				+ ", state: " + dialog.getState());

		// Processing response
		if (statusCode == Response.TRYING) {
			log.info("<<<<<<< 100 TRYING: dialog: " + this.dialog + ", state: "
					+ dialog.getState());
			// DO NOTHING
		} else if (statusCode == Response.RINGING) {
			log.info("<<<<<<< 180 Ringing: dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			call.remoteRingingCall();
		} else if (statusCode == Response.SESSION_PROGRESS) {
			log.info("<<<<<<< 183 Session Progress: dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			// DO NOTHING
		} else if (statusCode < 200) {
			log.info("<<<<<<< " + statusCode + " 1xx: dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			// DO NOTHING
		} else if (statusCode == Response.REQUEST_TERMINATED) {
			log.info("<<<<<<< " + statusCode + " TERMINATED: dialog: "
					+ this.dialog.getDialogId() + ", state: "
					+ dialog.getState());
			// Notify successfull call cancel
			log.info("<<<<<<< " + statusCode
					+ " Session cancel confirmed by remote peer: dialog: "
					+ this.dialog + ", state: " + dialog.getState());
			call.LocalCallCancel();
		} else if (statusCode == Response.BUSY_HERE
				|| statusCode == Response.BUSY_EVERYWHERE) {
			log.info("<<<<<<< " + statusCode + "Remote peer is BUSY: dialog: "
					+ this.dialog + ", state: " + dialog.getState());
			call.remoteCallBusy();

		} else if (statusCode == Response.TEMPORARILY_UNAVAILABLE

		|| statusCode == Response.DECLINE) {
			log.info("<<<<<<< " + statusCode
					+ "Session REJECT by remote peer: dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			call.remoteCallReject();

		} else if (statusCode == Response.UNSUPPORTED_MEDIA_TYPE
				|| statusCode == Response.NOT_ACCEPTABLE_HERE
				|| statusCode == Response.NOT_ACCEPTABLE) {
			log.info("<<<<<<< " + statusCode
					+ " UNSUPPORTED_MEDIA_TYPE: dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			call.unsupportedMediaType();
		} else if (statusCode == 476 || statusCode == Response.NOT_FOUND) {
			// USER_NOT_FOUND. SIP/2.0 476
			// Unresolvable destination
			log.info("<<<<<<< " + statusCode + " USER_NOT_FOUND: dialog: "
					+ this.dialog + ", state: " + dialog.getState());
			call.userNotFound();
		} else if (statusCode == Response.OK) {
			// 200 OK
			log.info("<<<<<<< 200 OK: dialog: " + this.dialog.getDialogId()
					+ ", state: " + dialog.getState());
			byte[] rawContent = response.getRawContent();
			int l = response.getContentLength().getContentLength();
			if (l != 0 && rawContent != null) {
				// SDP offer sent by invite request
				log.debug("Process SDP response from remote peer");
				processSdpAnswer(rawContent);
			} else {
				// Send ACK to complete INVITE transaction
				try {
					sendAck(null);
					call.completedCallWithError("INVITE response received with no SDP");
				} catch (KurentoSipException e) {
					String msg = "Unable to send ACK message";
					log.error(msg, e);
					call.callError(msg);
				}
				call.completedCallWithError("Received response to INVITE with no SDP");
			}
		} else if (statusCode > 200 && statusCode < 400) {
			// Unsupported codes
			log.info("<<<<<<< " + statusCode
					+ " Response code not supported : dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			call.completedCallWithError("Unssuported code:" + statusCode);
		} else {
			log.info("<<<<<<< " + statusCode
					+ " Response code not supported : dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			call.callError("Unsupported status code received:" + statusCode);
			// sendAck(); // ACK is automatically sent by the SIP Stack for
			// codes >4xx
		}
	}

	private void sendAck(byte[] sdp) throws KurentoSipException {
		// Non 2XX responses will cause the SIP Stack to send the ACK message
		// automatically
		if (!DialogState.CONFIRMED.equals(dialog.getState()))
			// Only dialogs in state confirm can send 200 OK ACKs
			return;
		try {
			// Send ACK
			Request ackRequest = dialog.createAck(((CSeqHeader) request
					.getHeader(CSeqHeader.NAME)).getSeqNumber());

			if (sdp != null) {
				ContentTypeHeader contentTypeHeader = sipUA.getHeaderFactory()
						.createContentTypeHeader("application", "SDP");
				ackRequest.setContent(sdp, contentTypeHeader);
			}
			dialog.sendAck(ackRequest);
			log.info("SIP send ACK\n" + ">>>>>>>>>> SIP send ACK >>>>>>>>>>\n"
					+ ackRequest.toString() + "\n"
					+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
		} catch (InvalidArgumentException e) {
			String msg = "Invalid Argument Exception while sending ACK for transaction: "
					+ this.dialog.getDialogId();
			throw new KurentoSipException(msg, e);
		} catch (SipException e) {
			String msg = "Sip Exception while sending ACK for transaction: "
					+ this.dialog.getDialogId();
			throw new KurentoSipException(msg, e);

		} catch (ParseException e) {
			String msg = "Unssupported SDP while sending ACK request for transaction: "
					+ this.dialog.getDialogId();
			throw new KurentoSipException(msg, e);

		}
	}

	private void processSdpAnswer(byte[] rawContent) {
		SessionDescription sdp = new SessionDescription(
				SessionDescription.Type.ANSWER, new String(rawContent));
		call.getPeerConnection().setRemoteDescription(new SdpObserver() {
			@Override
			public void onSuccess(SessionDescription arg0) {
				// Nothing to do
			}

			@Override
			public void onSuccess() {
				try {
					sendAck(null);
					call.completedCall();
				} catch (KurentoSipException e) {
					String msg = "Unable to send ACK message after SDP processing";
					log.error(msg, e);
					call.callError(msg);
				}
			}

			@Override
			public void onFailure(String error) {
				sipUA.getErrorHandler().onCallError(call,
						new KurentoException(error));
			}
		}, sdp);
	}

}
