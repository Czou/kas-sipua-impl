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
import java.util.ArrayList;
import java.util.List;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.AcceptHeader;
import javax.sip.header.AllowHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipCall;
import com.kurento.kas.sip.ua.SipUA;

public abstract class CTransaction extends Transaction {

	private static final int MAX_FORWARDS = 70;

	ClientTransaction clientTransaction;
	Request request;

	// General attributes
	private static final Logger log = LoggerFactory
			.getLogger(CTransaction.class.getSimpleName());

	String method;
	Dialog dialog;
	SipUA sipUA;
	SipCall call;

	String localUri;
	String remoteUri;

	// ////////////
	//
	// CONSTRUCTOR
	//
	// ////////////

	// Used for Non dialog transactions
	CTransaction(String method, SipUA sipUA, String localUri, String remoteUri,
			long cSeqNumber) throws KurentoSipException {
		this.sipUA = sipUA;
		this.method = method;
		this.localUri = localUri;
		this.remoteUri = remoteUri;
		CTransaction.cSeqNumber = cSeqNumber;
		createRequest();
	}

	CTransaction(String method, SipUA sipUA, SipCall call)
			throws KurentoSipException {
		this.method = method;
		this.sipUA = sipUA;
		this.call = call;
		this.dialog = call.getDialog();
		this.localUri = call.getLocalUri();
		this.remoteUri = call.getRemoteUri();
		createRequest();
	}

	// Used by Cancel transaction
	CTransaction(Request request, SipUA sipUA) throws KurentoSipException {
		this.sipUA = sipUA;
		this.request = request;
		createClientTransaction();
	}

	// //////////////
	//
	// GETTERS
	//
	// //////////////

	public ClientTransaction getClientTransaction() {
		return clientTransaction;
	}

	public Dialog getDialog() {
		return dialog;
	}

	// //////////////
	//
	// BUILD REQUEST
	//
	// //////////////

	void createRequest() throws KurentoSipException {
		// Check if dialog exists
		if (dialog == null) {
			try {
				CallIdHeader callIdHeader = buildCallIdHeader();
				FromHeader fromHeader = buildFromHeader();
				ToHeader toHeader = buildToHeader();
				List<ViaHeader> viaHeaders = buildViaHeaders();
				CSeqHeader cSeqHeader = buildCSeqHeader();
				MaxForwardsHeader maxForwardsHeader = buildMaxForwardsHeader();
				UserAgentHeader userAgentHeader = buildUserAgentHeader();
				ContactHeader contactHeader = buildContactHeader();

				SipURI requestURI = buildRequestURI();

				// Create Request
				request = sipUA.getMessageFactory().createRequest(requestURI,
						method, callIdHeader, cSeqHeader, fromHeader, toHeader,
						viaHeaders, maxForwardsHeader);
				request.addHeader(userAgentHeader);
				request.addHeader(contactHeader);

			} catch (ParseException e) {
				throw new KurentoSipException(
						"Parse error while creating SIP client transaction", e);
			} catch (InvalidArgumentException e) {
				throw new KurentoSipException(
						"Invalid argument for SIP client transaction", e);
			}
		} else {
			try {
				// Create Request from dialog
				request = dialog.createRequest(method);
			} catch (SipException e) {
				throw new KurentoSipException("Dialog is not yet established",
						e);
			}
		}

		createClientTransaction();
	}

	private void createClientTransaction() throws KurentoSipException {
		try {
			clientTransaction = sipUA.getSipProvider().getNewClientTransaction(
					request);
			clientTransaction.setApplicationData(this);

			// get dialog again
			dialog = clientTransaction.getDialog();
		} catch (TransactionUnavailableException e) {
			throw new KurentoSipException(
					"Transaction error while creating new client transaction",
					e);
		}
	}

	// // ALL THIS HELPER FUNCTIONS ARE CALLED WHEN DIALOG=NULL // //

	private CallIdHeader buildCallIdHeader() throws KurentoSipException {
		// Dialog is null here. Make sure you don't use it
		return sipUA.getSipProvider().getNewCallId();
	}

	FromHeader buildFromHeader() throws ParseException {
		// Dialog is null here. Make sure you don't use it
		String localTag = getNewRandomTag();
		Address localAddress = sipUA.getAddressFactory()
				.createAddress(localUri);
		return sipUA.getHeaderFactory()
				.createFromHeader(localAddress, localTag);
	}

	ToHeader buildToHeader() throws ParseException {
		// Dialog is null here. Make sure you don't use it
		Address remoteAddress = sipUA.getAddressFactory().createAddress(
				remoteUri);
		return sipUA.getHeaderFactory().createToHeader(remoteAddress, null);
	}

	List<ViaHeader> buildViaHeaders() throws ParseException,
			InvalidArgumentException {
		// Dialog is null here. Make sure you don't use it
		List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader viaHeader = sipUA.getHeaderFactory().createViaHeader(
				sipUA.getLocalAddress(), sipUA.getLocalPort(),
				sipUA.getPreferences().getSipTransport(), getNewRandomBranch());

		viaHeader.setRPort();
		// add via headers
		viaHeaders.add(viaHeader);
		return viaHeaders;
	}

	CSeqHeader buildCSeqHeader() throws ParseException,
			InvalidArgumentException {
		// Dialog is null here. Make sure you don't use it
		return sipUA.getHeaderFactory().createCSeqHeader(cSeqNumber, method);
	}

	MaxForwardsHeader buildMaxForwardsHeader() throws InvalidArgumentException {
		// Dialog is null here. Make sure you don't use it
		return sipUA.getHeaderFactory().createMaxForwardsHeader(MAX_FORWARDS);
	}

	AllowHeader buildAllowHeader() throws KurentoSipException {
		try {
			// Dialog is null here. Make sure you don't use it
			return sipUA.getHeaderFactory().createAllowHeader(
					"INVITE,ACK,CANCEL,BYE");
		} catch (ParseException e) {
			throw new KurentoSipException(
					"Parse Exception building Header Factory", e);
		}
	}

	SupportedHeader buildSupportedHeader() throws KurentoSipException {
		try {
			// Dialog is null here. Make sure you don't use it
			return sipUA.getHeaderFactory().createSupportedHeader("100rel");
		} catch (ParseException e) {
			throw new KurentoSipException(
					"Parse Exception building Support header", e);
		}

	}

	ContentTypeHeader buildContentTypeHeader() throws KurentoSipException {
		// Dialog is null here. Make sure you don't use it
		try {
			return sipUA.getHeaderFactory().createContentTypeHeader(
					"application", "sdp");
		} catch (ParseException e) {
			throw new KurentoSipException(
					"ParseException building contentType header", e);
		}
	}

	ContactHeader buildContactHeader() throws KurentoSipException {
		// Dialog is null here. Make sure you don't use it
		ContactHeader contact = sipUA.getHeaderFactory().createContactHeader(
				sipUA.getContactAddress(localUri));
		return contact;
	}

	AcceptHeader buildAcceptHeader() throws ParseException {
		// Dialog is null here. Make sure you don't use it
		return sipUA.getHeaderFactory()
				.createAcceptHeader("application", "sdp");
	}

	UserAgentHeader buildUserAgentHeader() throws ParseException {
		// Dialog is null here. Make sure you don't use it
		return sipUA.getUserAgentHeader();
	}

	SipURI buildRequestURI() throws ParseException {
		// Dialog is null here. Make sure you don't use it
		return (SipURI) sipUA.getAddressFactory().createAddress(remoteUri)
				.getURI();
	}

	// ///////////////////
	//
	// MANAGE TRANSACTION
	//
	// ///////////////////

	public void sendRequest() throws KurentoSipException {
		sendRequest(null);
	}

	public void sendRequest(String sdp) throws KurentoSipException {
		if (sdp != null && !sdp.isEmpty()) {
			try {
				request.setContent(sdp.getBytes(), buildContentTypeHeader());
			} catch (ParseException e) {
				throw new KurentoSipException(
						"ParseException adding content to INVITE request:\n"
								+ sdp, e);
			}
		}

		log.info("SIP send request\n"
				+ ">>>>>>>>>> SIP send request >>>>>>>>>>\n"
				+ clientTransaction.getRequest().toString()
				+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

		try {
			if (dialog != null
					&& DialogState.CONFIRMED.equals(dialog.getState()))
				dialog.sendRequest(clientTransaction);
			else
				clientTransaction.sendRequest();
		} catch (SipException e) {
			throw new KurentoSipException("Sip Exception sending  request", e);
		}
	}

	public abstract void processResponse(ResponseEvent event);

	public void processTimeout() {
		log.info("Client transaction timeout");
		if (call != null)
			call.callTimeout();
	}

}
