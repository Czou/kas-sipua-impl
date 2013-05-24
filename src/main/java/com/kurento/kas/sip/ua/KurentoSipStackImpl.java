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
import gov.nist.javax.sip.SipStackImpl;

import java.util.Properties;

import javax.sip.PeerUnavailableException;

import android.content.Context;

public class KurentoSipStackImpl extends SipStackImpl {

	public KurentoSipStackImpl(Context context,
			Properties configurationProperties) throws PeerUnavailableException {
		super(configurationProperties);
	}

	public void setNetworkLayer(NetworkLayer networkLayer) {
		this.networkLayer = networkLayer;
	}

}
