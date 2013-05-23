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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipUA;

public class SAck extends STransaction {

	private static final Logger log = LoggerFactory.getLogger(SAck.class
			.getSimpleName());

	// Process ACK to a successful INVITE
	public SAck(SipUA sipUA, ServerTransaction serverTransaction)
			throws KurentoSipException {
		super(sipUA, serverTransaction);

		// Process request
		Request request = serverTransaction.getRequest();

		log.debug("Invite transaction received a valid ACK");

		// Process ACK request
		if (getContentLength(request) == 0) {
			log.debug("ACK does not provides content. Call completes successfully");
			call.completedCall();
		} else {
			log.debug("ACK contains SDP response.");

			// TODO retrieve Descriptor offer from response
			// TODO create Descriptor response to send in ACK

		}
	}

}
