package com.kurento.kas.sip.ua;

import java.util.UUID;

import javax.sip.address.Address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.sip.util.KurentoUaTimerTask;
import com.kurento.kas.ua.Register;

public class SipRegister {

	private static final Logger log = LoggerFactory.getLogger(SipRegister.class
			.getSimpleName());

	private SipUA sipUA;
	private Register register;
	private long cseq;
	private UUID registerCallId;
	private Address address;
	private SipRegisterTimerTask sipRegisterTimerTask;

	public SipRegister(SipUA sipUA, Register register) {
		this.sipUA = sipUA;
		this.register = register;
		this.registerCallId = UUID.randomUUID();
		this.cseq = 1;
		this.sipRegisterTimerTask = new SipRegisterTimerTask();
	}

	public SipRegister(SipUA sipUA, Register register, Address address) {
		// instance-id: RFC5626. Used to set the registrarCallId
		/*
		 * According to RFC5626 instance-id must stay the same on UA reboot or
		 * power cycle. This implementation assigns temporal UUID that stays the
		 * same during UA's life cycle
		 */
		this(sipUA, register);
		this.address = address;
	}

	public Register getRegister() {
		return this.register;
	}

	public Address getAddress() {
		return this.address;
	}

	public void setAddress(Address address) {
		this.address = address;
	}

	public synchronized long getCseq() {
		return cseq++;
	}

	public String getRegisterCallId() {
		return registerCallId.toString();
	}

	public SipRegisterTimerTask getSipRegisterTimerTask() {
		return sipRegisterTimerTask;
	}

	private class SipRegisterTimerTask extends KurentoUaTimerTask {
		@Override
		public void run() {
			log.debug("SipRegisterTimerTask register");
			sipUA.register(register);
		}
	}

}
