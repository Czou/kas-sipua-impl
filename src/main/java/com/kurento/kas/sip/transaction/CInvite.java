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
import javax.sip.header.AllowHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.call.TerminatedCall.Reason;
import com.kurento.kas.call.impl.CallBase.CreateSdpOfferObserver;
import com.kurento.kas.call.impl.CallBase.SetRemoteSdpObserver;
import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipCall;
import com.kurento.kas.sip.ua.SipUA;
import com.kurento.kas.ua.KurentoException;

public class CInvite extends CTransaction {

	private static final Logger log = LoggerFactory.getLogger(CInvite.class
			.getSimpleName());

	public CInvite(SipUA sipUA, SipCall call) throws KurentoSipException {
		super(Request.INVITE, sipUA, call);

		// INVITE requires to increase sequence number
		CTransaction.cSeqNumber++;
		request.addHeader(buildAllowHeader());
		request.addHeader(buildSupportedHeader());

		log.debug("Creating offer...");
		CreateSdpOfferObserver o = new CreateSdpOfferObserver() {
			@Override
			public void onSdpOfferCreated(String sdp) {
				CInvite.this.call.removeCreateSdpOfferObserver(this);
				try {
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

	private AllowHeader buildAllowHeader() throws KurentoSipException {
		try {
			// Dialog is null here. Make sure you don't use it
			return sipUA.getHeaderFactory().createAllowHeader(
					"INVITE,ACK,CANCEL,BYE");
		} catch (ParseException e) {
			throw new KurentoSipException(
					"Parse Exception building Header Factory", e);
		}
	}

	private SupportedHeader buildSupportedHeader() throws KurentoSipException {
		try {
			// Dialog is null here. Make sure you don't use it
			return sipUA.getHeaderFactory().createSupportedHeader("100rel");
		} catch (ParseException e) {
			throw new KurentoSipException(
					"Parse Exception building Support header", e);
		}
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
			// Notify successful call cancel
			log.info("<<<<<<< " + statusCode
					+ " Session cancel confirmed by remote peer: dialog: "
					+ this.dialog + ", state: " + dialog.getState());
			call.terminatedCall(Reason.LOCAL_HANGUP);
		} else if (statusCode == Response.BUSY_HERE
				|| statusCode == Response.BUSY_EVERYWHERE) {
			log.info("<<<<<<< " + statusCode + "Remote peer is BUSY: dialog: "
					+ this.dialog + ", state: " + dialog.getState());
			call.terminatedCall(Reason.REMOTE_BUSY);
		} else if (statusCode == Response.TEMPORARILY_UNAVAILABLE
				|| statusCode == Response.DECLINE) {
			log.info("<<<<<<< " + statusCode
					+ "Session REJECT by remote peer: dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			call.terminatedCall(Reason.REMOTE_HANGUP);
		} else if (statusCode == Response.UNSUPPORTED_MEDIA_TYPE
				|| statusCode == Response.NOT_ACCEPTABLE_HERE
				|| statusCode == Response.NOT_ACCEPTABLE) {
			log.info("<<<<<<< " + statusCode
					+ " UNSUPPORTED_MEDIA_TYPE: dialog: " + this.dialog
					+ ", state: " + dialog.getState());
			call.terminatedCall(Reason.ERROR);
		} else if (statusCode == 476 || statusCode == Response.NOT_FOUND) {
			// USER_NOT_FOUND. SIP/2.0 476
			// Unresolvable destination
			log.info("<<<<<<< " + statusCode + " USER_NOT_FOUND: dialog: "
					+ this.dialog + ", state: " + dialog.getState());
			call.terminatedCall(Reason.USER_NOT_FOUND);
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
				} catch (KurentoSipException e) {
					String msg = "Unable to send ACK message";
					log.error(msg, e);
					call.terminatedCall(Reason.ERROR);
				} finally {
					call.completedCallWithError("INVITE response received with no SDP");
				}
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
			log.error("Unsupported status code received:" + statusCode);
			call.terminatedCall(Reason.ERROR);
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
		call.setRemoteSdp(new String(rawContent), new SetRemoteSdpObserver() {
			@Override
			public void onSuccess() {
				try {
					sendAck(null);
					call.completedCall();
				} catch (KurentoSipException e) {
					String msg = "Unable to send ACK message after SDP processing";
					log.error(msg, e);
					call.terminatedCall(Reason.ERROR);
				}
			}

			@Override
			public void onError(KurentoException error) {
				sipUA.getErrorHandler().onCallError(call, error);
			}
		});
	}

}
