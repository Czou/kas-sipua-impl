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

import gov.nist.javax.sip.header.ContentLength;

import javax.sip.Dialog;
import javax.sip.ServerTransaction;
import javax.sip.address.Address;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipCall;
import com.kurento.kas.sip.ua.SipUA;

public abstract class STransaction extends Transaction {

	private static final Logger log = LoggerFactory
			.getLogger(STransaction.class.getSimpleName());

	ServerTransaction serverTransaction;
	Request request;

	String method;
	SipCall call;
	Dialog dialog;
	SipUA sipUA;

	STransaction(SipUA sipUA, ServerTransaction serverTransaction)
			throws KurentoSipException {

		this.sipUA = sipUA;
		this.serverTransaction = serverTransaction;
		this.request = serverTransaction.getRequest();
		this.method = request.getMethod();
		this.dialog = serverTransaction.getDialog();
		if (dialog != null)
			this.call = (SipCall) dialog.getApplicationData();

		// Only SIP Version = 2.0 supported
		if (!isSipVersion2()) {
			sendResponse(Response.VERSION_NOT_SUPPORTED, null);
			throw new KurentoSipException("Sip version not supported: "
					+ serverTransaction.getRequest().getSIPVersion());
		}
	}

	public ServerTransaction getServerTransaction() {
		return serverTransaction;
	}

	/**
	 * Generate a response outside a SIP dialog
	 * 
	 * @param code
	 * @param sdp
	 * @throws ServerInternalErrorException
	 */
	public void sendResponse(int code, byte[] sdp) throws KurentoSipException {
		Response response;
		try {
			response = sipUA.getMessageFactory().createResponse(code,
					serverTransaction.getRequest());

			// Set user agent header
			if (sipUA.getUserAgentHeader() != null) {
				response.setHeader(sipUA.getUserAgentHeader());
			}

			// Set to tag
			String localTag = null;
			if (dialog != null && dialog.getLocalTag() != null)
				localTag = dialog.getLocalTag();

			if (localTag == null)
				localTag = getNewRandomTag();

			ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
			toHeader.setTag(localTag);

			ContactHeader contactHeader = buildContactHeader();
			response.setHeader(contactHeader);

			if (sdp != null) {
				ContentTypeHeader contentTypeHeader = sipUA.getHeaderFactory()
						.createContentTypeHeader("application", "SDP");
				response.setContent(sdp, contentTypeHeader);
			}

			log.info("SIP response send\n"
					+ ">>>>>>> SIP response send >>>>>>>\n" + "\tTRANSACTION: "
					+ serverTransaction.getBranchId() + "\n" + "\t CODE: "
					+ response.getStatusCode() + "\n" + response.toString()
					+ "\n" + ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

			serverTransaction.sendResponse(response);
			log.info("Transaction goes to state: "
					+ serverTransaction.getState());
		} catch (Exception e) {
			log.error(
					"Found problems to build and send transaction response code: "
							+ code, e);
			if (code == Response.SERVER_INTERNAL_ERROR) {
				throw new KurentoSipException(
						"Unable to send response code 500. GIVE UP!!!", e);
			} else {
				sendResponse(Response.SERVER_INTERNAL_ERROR, null);
			}
		}
	}

	private ContactHeader buildContactHeader() throws KurentoSipException {
		String localParty = serverTransaction.getDialog().getLocalParty()
				.getURI().toString();
		Address contact = sipUA.getContactAddress(localParty);
		return sipUA.getHeaderFactory().createContactHeader(contact);
	}

	int getContentLength(Request request) throws KurentoSipException,
			KurentoSipException {
		// Check if invites provides a SDP
		ContentLength clHeader = (ContentLength) request
				.getHeader(ContentLength.NAME);
		if (clHeader == null) {
			sendResponse(Response.BAD_REQUEST, null);
			throw new KurentoSipException(
					"Unable to find Content-length in Server Request");
		} else {
			return clHeader.getContentLength();
		}
	}

	private boolean isSipVersion2() {
		if (serverTransaction.getRequest().getSIPVersion().equals("SIP/2.0")) {
			return true;
		} else {
			return false;
		}
	}

	public void processTimeout() {
		log.info("Server transaction timeout");
		if (call != null) {
			call.callTimeout();
		}
	}

}
