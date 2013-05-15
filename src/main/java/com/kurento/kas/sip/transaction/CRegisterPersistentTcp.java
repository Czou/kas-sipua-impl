package com.kurento.kas.sip.transaction;

import javax.sip.ResponseEvent;
import javax.sip.header.ViaHeader;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.sip.ua.KurentoSipException;
import com.kurento.kas.sip.ua.SipRegister;
import com.kurento.kas.sip.ua.SipUA;
import com.kurento.kas.ua.KurentoException;

public class CRegisterPersistentTcp extends CRegister {

	private static final Logger log = LoggerFactory
			.getLogger(CRegisterPersistentTcp.class.getSimpleName());

	public CRegisterPersistentTcp(SipUA sipUA, SipRegister sipRegister,
			int expires) throws KurentoException, KurentoSipException {
		super(sipUA, sipRegister, expires);
	}

	@Override
	public void processResponse(ResponseEvent event) {
		Response response = event.getResponse();
		ViaHeader viaHeader = (ViaHeader) response.getHeader("Via");
		if (viaHeader != null) {
			int rport = viaHeader.getRPort();
			String received = viaHeader.getReceived();
			log.debug("rport: " + rport);
			log.debug("received: " + received);
			if (expires == 0 || rport != sipUA.getPublicPort()
					|| !received.equalsIgnoreCase(sipUA.getPublicAddress())) {
				sipUA.setPublicPort(rport);
				sipUA.setPublicAddress(received);
				sipUA.updateContactAddress(localUri);
				sipUA.registerPersistentTcp(sipRegister, sipUA.getPreferences()
						.getSipRegExpires());
				return;
			}
		}

		super.processResponse(event);
	}
}
