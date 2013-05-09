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

import javax.sip.ResponseEvent;
import javax.sip.message.Request;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipCall;
import com.kurento.kas.sip.ua.SipUA;

public class CBye extends CTransaction {

	public CBye(SipUA sipUA, SipCall call) throws KurentoSipException {
		super(Request.BYE, sipUA, call);
		sendRequest();
	}

	@Override
	public void processResponse(ResponseEvent event) {
		// DO NOTHING
	}

}
