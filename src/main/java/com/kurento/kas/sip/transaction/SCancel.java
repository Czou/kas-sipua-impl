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
import javax.sip.message.Response;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipUA;

public class SCancel extends STransaction {

	public SCancel(SipUA sipUA, ServerTransaction serverTransaction)
			throws KurentoSipException {
		super(sipUA, serverTransaction);

		// RFC 3261 - 9.2
		if (call == null)
			// No matching transaction ==> null SipContext & 481 response code
			sendResponse(Response.CALL_OR_TRANSACTION_DOES_NOT_EXIST);
		else {
			// Matching transaction found ==> send 200 code regardless of dialog
			// status
			sendResponse(Response.OK);
			call.remoteCallCancel();
		}
	}

}
