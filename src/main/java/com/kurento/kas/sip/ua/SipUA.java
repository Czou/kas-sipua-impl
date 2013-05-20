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

import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.SipStackImpl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.UserAgentHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.PeerConnectionFactory;

import android.app.AlarmManager;
import android.content.Context;

import com.kurento.kas.conference.Conference;
import com.kurento.kas.sip.transaction.CInvite;
import com.kurento.kas.sip.transaction.CRegister;
import com.kurento.kas.sip.transaction.CRegisterPersistentTcp;
import com.kurento.kas.sip.transaction.CTransaction;
import com.kurento.kas.sip.transaction.SAck;
import com.kurento.kas.sip.transaction.SBye;
import com.kurento.kas.sip.transaction.SCancel;
import com.kurento.kas.sip.transaction.SInvite;
import com.kurento.kas.sip.transaction.STransaction;
import com.kurento.kas.sip.util.AlarmUaTimer;
import com.kurento.kas.sip.util.KurentoUaTimerTask;
import com.kurento.kas.sip.util.NetworkUtilities;
import com.kurento.kas.ua.Call;
import com.kurento.kas.ua.CallDialingHandler;
import com.kurento.kas.ua.CallEstablishedHandler;
import com.kurento.kas.ua.CallRingingHandler;
import com.kurento.kas.ua.CallTerminatedHandler;
import com.kurento.kas.ua.ErrorHandler;
import com.kurento.kas.ua.KurentoException;
import com.kurento.kas.ua.Register;
import com.kurento.kas.ua.RegisterHandler;
import com.kurento.kas.ua.UA;

public class SipUA extends UA {

	private static final Logger log = LoggerFactory.getLogger(SipUA.class
			.getSimpleName());

	private static final String USER_AGENT = "KurentoAndroidUa/1.0.0";
	private UserAgentHeader userAgentHeader;

	// SIP factories
	private SipFactory sipFactory;
	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;

	// Sip Stack
	private SipProvider sipProvider;
	private SipStackExt sipStack;
	private SipListenerImpl sipListenerImpl = new SipListenerImpl();

	private AlarmUaTimer wakeupTimer;
	private AlarmUaTimer noWakeupTimer;

	private InetAddress localAddress;
	private SocketAddress tcpSocketAddress;

	private SipKeepAliveTimerTask sipKeepAliveTimerTask;
	private CheckTCPConnectionAliveTimerTask checkTcpConnectionAliveTimerTask;

	private static final int CHECK_TCP_CONNECTION_ALIVE_PERIOD = 2000; // milliseconds

	private int publicPort = -1;
	private String publicAddress = "";

	// Handlers
	private ErrorHandler errorHandler;
	private RegisterHandler registerHandler;
	private CallDialingHandler callDialingHandler;
	private CallEstablishedHandler callEstablishedHandler;
	private CallRingingHandler callRingingHandler;
	private CallTerminatedHandler callTerminatedHandler;

	private Map<String, SipRegister> localUris = new ConcurrentHashMap<String, SipRegister>();

	private Preferences preferences;

	public SipUA(Context context) throws KurentoSipException {
		super(context);

		try {
			preferences = new Preferences(context);
			sipFactory = SipFactory.getInstance();
			addressFactory = sipFactory.createAddressFactory();
			headerFactory = sipFactory.createHeaderFactory();
			messageFactory = sipFactory.createMessageFactory();

			userAgentHeader = headerFactory
					.createUserAgentHeader(new ArrayList<String>() {
						private static final long serialVersionUID = 1L;
						{
							add(USER_AGENT);
						}
					});

			this.wakeupTimer = new AlarmUaTimer(context,
					AlarmManager.ELAPSED_REALTIME_WAKEUP);
			this.noWakeupTimer = new AlarmUaTimer(context,
					AlarmManager.ELAPSED_REALTIME);
			createDefaultHandlers();
			configureSipStack();

			PeerConnectionFactory.initializeAndroidGlobals(context);
		} catch (Throwable t) {
			log.error("SipUA initialization error", t);
			throw new KurentoSipException("SipUA initialization error", t);
		}
	}

	@Override
	public void terminate() {
		if (sipKeepAliveTimerTask != null) {
			log.info("Stopping SIP keep alive");
			wakeupTimer.cancel(sipKeepAliveTimerTask);
		}

		if (checkTcpConnectionAliveTimerTask != null) {
			log.info("Stopping TCP connection alive");
			noWakeupTimer.cancel(checkTcpConnectionAliveTimerTask);
		}

		terminateSipStack();
	}

	// ////////////////
	//
	// GETTERS & SETTERS
	//
	// ////////////////

	public Preferences getPreferences() {
		return preferences;
	}

	public String getLocalAddress() {
		// TODO Return local address depending on STUN config
		return localAddress.getHostAddress();
	}

	public int getLocalPort() {
		// TODO Return local port depending on STUN config
		return preferences.getSipLocalPort();
	}

	public int getPublicPort() {
		if (publicPort == -1)
			return getLocalPort();
		return publicPort;
	}

	public void setPublicPort(int publicPort) {
		this.publicPort = publicPort;
	}

	public String getPublicAddress() {
		if ("".equals(publicAddress))
			return getLocalAddress();
		return publicAddress;
	}

	public void setPublicAddress(String publicAddress) {
		this.publicAddress = publicAddress;
	}

	public AlarmUaTimer getWakeupTimer() {
		return wakeupTimer;
	}

	public AddressFactory getAddressFactory() {
		return addressFactory;
	}

	public HeaderFactory getHeaderFactory() {
		return headerFactory;
	}

	public MessageFactory getMessageFactory() {
		return messageFactory;
	}

	public UserAgentHeader getUserAgentHeader() {
		return userAgentHeader;
	}

	public SipProvider getSipProvider() {
		return sipProvider;
	}

	public Address getContactAddress(String contactUri) {
		SipRegister sipReg = localUris.get(contactUri);
		if (sipReg != null)
			return sipReg.getAddress();
		return null;
	}

	public void updateContactAddress(String contactUri) {
		SipRegister sipReg = localUris.get(contactUri);
		if (sipReg != null) {
			try {
				Address contactAddress = addressFactory.createAddress("sip:"
						+ sipReg.getRegister().getUser() + "@"
						+ getPublicAddress() + ":" + getPublicPort()
						+ ";transport=" + preferences.getSipTransport());
				sipReg.setAddress(contactAddress);
			} catch (ParseException e) {
				log.error("Unable to update contact address", e);
			}
		}
	}

	// ////////////////
	//
	// HANDLERS
	//
	// ////////////////

	@Override
	public void setRegisterHandler(RegisterHandler registerHandler) {
		this.registerHandler = registerHandler;
	}

	@Override
	public void setExceptionHandler(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	@Override
	public void setCallDialingHandler(CallDialingHandler callDialingHandler) {
		this.callDialingHandler = callDialingHandler;
	}

	@Override
	public void setCallRingingHandler(CallRingingHandler callRingingHandler) {
		this.callRingingHandler = callRingingHandler;
	}

	@Override
	public void setCallEstablishedHandler(
			CallEstablishedHandler callEstablishedHandler) {
		this.callEstablishedHandler = callEstablishedHandler;
	}

	@Override
	public void setCallTerminatedHander(
			CallTerminatedHandler callTerminatedHandler) {
		this.callTerminatedHandler = callTerminatedHandler;
	}

	public ErrorHandler getErrorHandler() {
		return errorHandler;
	}

	public RegisterHandler getRegisterHandler() {
		return registerHandler;
	}

	public CallDialingHandler getCallDialingHandler() {
		return callDialingHandler;
	}

	public CallEstablishedHandler getCallEstablishedHandler() {
		return callEstablishedHandler;
	}

	public CallRingingHandler getCallRingingHandler() {
		return callRingingHandler;
	}

	public CallTerminatedHandler getCallTerminatedHandler() {
		return callTerminatedHandler;
	}

	// ////////////////////////////
	//
	// SIP STACK & INITIALIZATION
	//
	// ////////////////////////////

	private void configureSipStack() throws KurentoSipException {
		try {
			terminateSipStack(); // Just in case

			localAddress = NetworkUtilities.getLocalInterface(null,
					preferences.isSipOnlyIpv4());

			// TODO Find configuration that supports TLS / DTLS
			// TODO Find configuration that supports TCP with persistent
			// connection
			log.info("starting JAIN-SIP stack initializacion ...");

			Properties jainProps = new Properties();

			String outboundProxy = preferences.getSipProxyServerAddress() + ":"
					+ preferences.getSipProxyServerPort() + "/"
					+ preferences.getSipTransport();
			jainProps.setProperty("javax.sip.OUTBOUND_PROXY", outboundProxy);

			jainProps.setProperty("javax.sip.STACK_NAME",
					"siplib_" + System.currentTimeMillis());
			jainProps.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER",
					"true");

			jainProps.setProperty(
					"gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "true"); // By
																			// default
			jainProps.setProperty(
					"gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "true"); // By
																			// default
			jainProps.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "100");

			log.info("Stack properties: " + jainProps);

			// Create SIP STACK
			sipStack = new SipStackImpl(jainProps);
			// TODO get socket from SipStackExt to perform STUN test
			// TODO Verify socket transport to see if it is compatible with STUN

			// Create a listening point per interface
			log.info("Create listening point at: " + localAddress + ":"
					+ preferences.getSipLocalPort() + "/"
					+ preferences.getSipTransport());
			ListeningPoint listeningPoint = sipStack.createListeningPoint(
					localAddress.getHostAddress(),
					preferences.getSipLocalPort(),
					preferences.getSipTransport());
			// listeningPoint.setSentBy(publicAddress + ":" + publicPort);

			// Create SIP PROVIDER and add listening points
			sipProvider = sipStack.createSipProvider(listeningPoint);

			// Add User Agent as listener for the SIP provider
			sipProvider.addSipListener(sipListenerImpl);

			if (ListeningPoint.TCP.equalsIgnoreCase(preferences
					.getSipTransport())) {
				// rfc5626 3.5.1. CRLF Keep-Alive Technique
				// Only with connection-oriented
				if (sipKeepAliveTimerTask != null) {
					// Disable keep alive if already active
					wakeupTimer.cancel(sipKeepAliveTimerTask);
				}
				if (preferences.isEnableSipKeepAlive()) {
					log.info("Using SIP keep alive");
					sipKeepAliveTimerTask = new SipKeepAliveTimerTask(
							listeningPoint, preferences);
					long period = preferences.getSipKeepAliveSeconds() * 1000;
					wakeupTimer.schedule(sipKeepAliveTimerTask, period, period);
				}

				tcpSocketAddress = sipStack.obtainLocalAddress(
						InetAddress.getAllByName(preferences
								.getSipProxyServerAddress())[0], preferences
								.getSipProxyServerPort(), localAddress, 0);
				log.debug("Socket address: " + tcpSocketAddress);

				if (sipKeepAliveTimerTask != null)
					noWakeupTimer.cancel(checkTcpConnectionAliveTimerTask);

				checkTcpConnectionAliveTimerTask = new CheckTCPConnectionAliveTimerTask();
				noWakeupTimer.schedule(checkTcpConnectionAliveTimerTask,
						CHECK_TCP_CONNECTION_ALIVE_PERIOD,
						CHECK_TCP_CONNECTION_ALIVE_PERIOD);
			}

			// Re-register all local contacts
			for (SipRegister reg : localUris.values())
				register(reg.getRegister());

		} catch (Throwable t) {
			terminateSipStack();
			throw new KurentoSipException("Unable to instantiate a SIP stack",
					t);
		}
	}

	private void reconfigureSipStack() {
		// TODO: add some retries?
		try {
			log.info("Reconfigure SIP stack");
			configureSipStack();
		} catch (KurentoSipException e) {
			log.error("Error reconfiguring SIP stack", e);
			errorHandler.onUAError(SipUA.this, new KurentoException(e));
		}
	}

	private void terminateSipStack() {
		if (sipStack != null && sipProvider != null) {
			log.info("Delete SIP listening points");

			for (ListeningPoint lp : sipProvider.getListeningPoints()) {
				try {
					sipStack.deleteListeningPoint(lp);
				} catch (ObjectInUseException e) {
					log.warn("Unable to delete SIP listening point: "
							+ lp.getIPAddress() + ":" + lp.getPort());
				}
			}

			sipProvider.removeSipListener(sipListenerImpl);
			try {
				sipStack.deleteSipProvider(sipProvider);
			} catch (ObjectInUseException e) {
				log.warn("Unable to delete SIP provider");
			}
			sipStack.stop();
			sipProvider = null;
			sipStack = null;
			log.info("SIP stack terminated");
		}
	}

	// ////////////////
	//
	// URI & REGISTER MANAGEMENT
	//
	// ////////////////

	public void registerPersistentTcp(SipRegister sipReg, int expires) {
		try {
			CRegisterPersistentTcp cunreg = new CRegisterPersistentTcp(this,
					sipReg, expires);
			cunreg.sendRequest();
		} catch (KurentoSipException e) {
			log.error("Unable to register", e);
			registerHandler.onRegisterError(sipReg.getRegister(),
					new KurentoException(e));
		} catch (KurentoException e) {
			log.error("Unable to create CRegisterPersistentTcp", e);
			registerHandler.onRegisterError(sipReg.getRegister(), e);
		}
	}

	@Override
	public void register(Register register) {
		// TODO Implement URI register
		// TODO Create contact address on register "sip:userName@address:port"
		// TODO Implement STUN in order to get public transport address. This
		// is not accurate at all, but at least give the chance
		// TODO STUN enabled then use public, STUN disabled then use private.
		// Do not check NAT type.

		try {
			log.debug("Request to register: " + register.getUri() + " for "
					+ preferences.getSipRegExpires() + " seconds.");

			SipRegister sipReg = localUris.get(register.getUri());
			if (sipReg == null) {
				log.debug("There is not a previous register for "
						+ register.getUri() + ". Create new register.");

				String contactAddressStr = "sip:" + register.getUser() + "@"
						+ localAddress.getHostAddress() + ":"
						+ preferences.getSipLocalPort();
				if (!ListeningPoint.UDP.equalsIgnoreCase(preferences
						.getSipTransport()))
					contactAddressStr += ";transport="
							+ preferences.getSipTransport();

				Address contactAddress = addressFactory
						.createAddress(contactAddressStr);
				sipReg = new SipRegister(this, register, contactAddress);
				log.debug("Add into localUris " + register.getUri());
				localUris.put(register.getUri(), sipReg);
			}

			// Before registration remove previous timers
			wakeupTimer.cancel(sipReg.getSipRegisterTimerTask());

			if (ListeningPoint.TCP.equalsIgnoreCase(preferences
					.getSipTransport())) {
				registerPersistentTcp(sipReg, 0);
			} else {
				CRegister creg = new CRegister(this, sipReg,
						preferences.getSipRegExpires());
				creg.sendRequest();
			}
		} catch (ParseException e) {
			log.error("Unable to create contact address", e);
			registerHandler.onRegisterError(register, new KurentoException(e));
		} catch (KurentoSipException e) {
			log.error("Unable to register", e);
			registerHandler.onRegisterError(register, new KurentoException(e));
		} catch (KurentoException e) {
			log.error("Unable to create CRegister", e);
			registerHandler.onRegisterError(register, e);
		}
	}

	@Override
	public void unregister(Register register) {
		try {
			log.debug("Request to unregister: " + register.getUri());

			SipRegister sipReg = localUris.get(register.getUri());
			if (sipReg == null) {
				log.warn("There is not a previous register for "
						+ register.getUri());
				registerHandler.onUserOffline(register);
				return;
			}

			wakeupTimer.cancel(sipReg.getSipRegisterTimerTask());
			CRegister creg = new CRegister(this, sipReg, 0);
			creg.sendRequest();
			localUris.remove(register.getUri());
		} catch (KurentoSipException e) {
			log.error("Unable to register", e);
			registerHandler.onRegisterError(register, new KurentoException(e));
		} catch (KurentoException e) {
			log.error("Unable to create CRegister", e);
			registerHandler.onRegisterError(register, e);
		}
	}

	@Override
	public Call dial(String fromUri, String remoteUri) {
		SipCall call = null;

		if (remoteUri != null) {
			log.debug("Creating new SipCall");
			try {
				call = new SipCall(this, fromUri, remoteUri);
				new CInvite(this, call);
			} catch (KurentoSipException e) {
				errorHandler.onCallError(call, new KurentoException(e));
			}
		} else {
			errorHandler.onCallError(null, new KurentoException(
					"Request to call NULL uri."));
		}

		return call;
	}

	private class SipListenerImpl implements SipListener {

		@Override
		public void processDialogTerminated(DialogTerminatedEvent arg0) {
			// Nothing to do here
			log.info("Dialog Terminated. Perform clean up operations");
		}

		@Override
		public void processIOException(IOExceptionEvent arg0) {
			// Nothing to do here
			log.info("IO Exception");
		}

		@Override
		public void processRequest(RequestEvent requestEvent) {
			log.info("SIP request received\n"
					+ "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n"
					+ requestEvent.getRequest().toString() + "\n"
					+ "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

			ServerTransaction serverTransaction;
			try {
				if ((serverTransaction = requestEvent.getServerTransaction()) == null) {
					// Create transaction
					serverTransaction = sipProvider
							.getNewServerTransaction(requestEvent.getRequest());
				}
			} catch (TransactionAlreadyExistsException e) {
				log.warn("Request already has an active transaction. It shouldn't be delivered by SipStack to the SIPU-UA");
				return;
			} catch (TransactionUnavailableException e) {
				log.warn("Unable to get ServerTransaction for request");
				return;
			}

			try {
				// Check if this transaction addressed to this UA
				Dialog dialog = serverTransaction.getDialog();
				if (dialog != null) {
					String requestUri = serverTransaction.getDialog()
							.getLocalParty().getURI().toString();
					if (!localUris.containsKey(requestUri)) {
						// Request is addressed to unknown URI
						log.info("SIP transaction for unknown URI: "
								+ requestUri);
						Response response = messageFactory.createResponse(
								Response.NOT_FOUND,
								serverTransaction.getRequest());
						serverTransaction.sendResponse(response);
						return;
					}
				}

				// Check if the SipCAll has to be created
				if (dialog != null && dialog.getApplicationData() == null) {
					log.debug("Create SipCall for transaction: "
							+ serverTransaction.getBranchId());
					SipCall call = new SipCall(SipUA.this, dialog);
					dialog.setApplicationData(call);
				} else {
					log.debug("Transaccion already has an associated SipCall");
				}

				// Get Request method to create a proper transaction record
				STransaction sTrns = (STransaction) serverTransaction
						.getApplicationData();
				log.debug("sTrns: " + sTrns);
				if (sTrns == null) {
					String reqMethod = requestEvent.getRequest().getMethod();
					if (reqMethod.equals(Request.ACK)) {
						log.info("Detected ACK request");
						sTrns = new SAck(SipUA.this, serverTransaction);
					} else if (reqMethod.equals(Request.INVITE)) {
						log.info("Detected INVITE request");
						sTrns = new SInvite(SipUA.this, serverTransaction);
					} else if (reqMethod.equals(Request.BYE)) {
						log.info("Detected BYE request");
						sTrns = new SBye(SipUA.this, serverTransaction);
					} else if (reqMethod.equals(Request.CANCEL)) {
						log.info("Detected CANCEL request");
						sTrns = new SCancel(SipUA.this, serverTransaction);
					} else {
						log.error("Unsupported method on request: " + reqMethod);
						Response response = messageFactory.createResponse(
								Response.NOT_IMPLEMENTED,
								requestEvent.getRequest());
						serverTransaction.sendResponse(response);
					}
					// Insert application data into server transaction
					serverTransaction.setApplicationData(sTrns);
				}
			} catch (Exception e) {
				log.warn("Unable to process server transaction", e);
			}
		}

		@Override
		public void processResponse(ResponseEvent responseEvent) {
			log.info("\n" + "<<<<<<<< SIP response received <<<<<<\n"
					+ responseEvent.getResponse().toString()
					+ "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

			// Get transaction record for this response and process response
			// SipProvider searches a proper client transaction to each
			// response.
			// if any is found it gives without any transaction
			ClientTransaction clientTransaction = responseEvent
					.getClientTransaction();
			if (clientTransaction == null) {
				// SIP JAIN was unable to find a proper transaction for this
				// response. The UAC will discard silently the request as stated
				// by RFC3261 18.1.2
				log.error("Unable to find a proper transaction matching response");
				return;
			}

			// Get the transaction application record and process response.
			CTransaction cTrns = (CTransaction) clientTransaction
					.getApplicationData();
			if (cTrns == null) {
				log.error("Server Internal Error (500): Empty application data for response transaction");
			}
			cTrns.processResponse(responseEvent);
		}

		@Override
		public void processTimeout(TimeoutEvent timeoutEvent) {
			log.warn("Transaction timeout:" + timeoutEvent.toString());
			try {
				if (timeoutEvent.getClientTransaction() != null) {
					CTransaction cTrns = (CTransaction) timeoutEvent
							.getClientTransaction().getApplicationData();
					if (cTrns != null)
						cTrns.processTimeout();
					timeoutEvent.getClientTransaction().terminate();
				} else if (timeoutEvent.getServerTransaction() != null) {
					STransaction sTrns = (STransaction) timeoutEvent
							.getClientTransaction().getApplicationData();
					if (sTrns != null)
						sTrns.processTimeout();
					timeoutEvent.getServerTransaction().terminate();
				}
			} catch (ObjectInUseException e) {
				log.error("Unable to handle timeouts");
			}
		}

		@Override
		public void processTransactionTerminated(
				TransactionTerminatedEvent trnsTerminatedEv) {
			if (trnsTerminatedEv.isServerTransaction()) {
				log.info("Server Transaction terminated with ID: "
						+ trnsTerminatedEv.getServerTransaction().getBranchId());
			} else {
				log.info("Client Transaction terminated with ID: "
						+ trnsTerminatedEv.getClientTransaction().getBranchId());
			}
		}
	}

	private void createDefaultHandlers() {
		errorHandler = new ErrorHandler() {

			@Override
			public void onUAError(UA ua, KurentoException exception) {
				log.info("Default onUAError");
			}

			@Override
			public void onConfError(Conference conference,
					KurentoException exception) {
				log.info("Default onConfError");
			}

			@Override
			public void onCallError(Call call, KurentoException exception) {
				log.info("Default onCallError");
			}
		};

		registerHandler = new RegisterHandler() {

			@Override
			public void onUserOnline(Register register) {
				log.info("Default onUserOnline");
			}

			@Override
			public void onUserOffline(Register register) {
				log.info("Default onUserOffline");
			}

			@Override
			public void onAuthenticationFailure(Register register) {
				log.info("Default onAuthenticationFailure");
			}

			@Override
			public void onRegisterError(Register register,
					KurentoException exception) {
				log.info("Default onRegisterError");
			}

		};

		callDialingHandler = new CallDialingHandler() {

			@Override
			public void onRemoteRinging(Call dialingCall) {
				log.info("Default onRemoteRinging");
			}
		};

		callEstablishedHandler = new CallEstablishedHandler() {

			@Override
			public void onEstablished(Call call) {
				log.info("Default onEstablished");
			}
		};

		callRingingHandler = new CallRingingHandler() {

			@Override
			public void onRinging(Call ringinCall) {
				log.info("Default onRinging");
			}
		};

		callTerminatedHandler = new CallTerminatedHandler() {

			@Override
			public void onTerminate(Call terminatedCall) {
				log.info("Default onTerminate");
			}
		};
	}

	private class SipKeepAliveTimerTask extends KurentoUaTimerTask {

		private ListeningPointExt listeningPoint;
		private String proxyAddr;
		private int proxyPort;

		public SipKeepAliveTimerTask(ListeningPoint listeningPoint,
				Preferences preferences) {
			this.listeningPoint = (ListeningPointExt) listeningPoint;
			this.proxyAddr = preferences.getSipProxyServerAddress();
			this.proxyPort = preferences.getSipProxyServerPort();
		}

		@Override
		public void run() {
			log.debug("Sending SIP keep alive");
			try {
				listeningPoint.sendHeartbeat(proxyAddr, proxyPort);
			} catch (IOException e) {
				log.error("Unable to send SIP keep-alive message", e);
				reconfigureSipStack();
			}
		}
	}

	private class CheckTCPConnectionAliveTimerTask extends KurentoUaTimerTask {

		@Override
		public void run() {
			log.trace("------------ Check TCP Connection Alive ------------");
			try {
				SocketAddress sa = sipStack.obtainLocalAddress(
						InetAddress.getAllByName(preferences
								.getSipProxyServerAddress())[0], preferences
								.getSipProxyServerPort(), localAddress, 0);
				if (!tcpSocketAddress.toString()
						.equalsIgnoreCase(sa.toString())) {
					log.debug("Socket address changed: " + tcpSocketAddress
							+ " -> " + sa);
					reconfigureSipStack();
				}
			} catch (UnknownHostException e) {
				log.warn("Unknown host", e);
			} catch (IOException e) {
				log.error("Error while obtaining local address", e);
				reconfigureSipStack();
			}
		}
	}

}
