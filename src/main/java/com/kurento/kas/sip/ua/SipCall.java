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

import java.util.UUID;

import javax.sip.Dialog;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurento.kas.call.DialingCall;
import com.kurento.kas.call.RingingCall;
import com.kurento.kas.call.RingingCall.RejectCode;
import com.kurento.kas.call.TerminatedCall;
import com.kurento.kas.call.TerminatedCall.Reason;
import com.kurento.kas.call.impl.CallBase;
import com.kurento.kas.sip.transaction.CBye;
import com.kurento.kas.sip.transaction.CCancel;
import com.kurento.kas.sip.transaction.CTransaction;
import com.kurento.kas.sip.transaction.STransaction;
import com.kurento.kas.sip.util.LooperThread;
import com.kurento.kas.ua.KurentoException;

//TODO: callbacks from a pool of threads
public class SipCall extends CallBase {

	protected static final Logger log = LoggerFactory.getLogger(SipCall.class
			.getSimpleName());

	private enum State {
		IDLE, INCOMING_RINGING, OUTGOING_RINGING, CONFIRMED, TERMINATED
	}

	private SipRingingCall sipRingingCall = new SipRingingCall();
	SipDialingCall sipDialingCall = new SipDialingCall();
	private SipEstablishedCall sipEstablishedCall = new SipEstablishedCall();
	private SipTerminatedCall sipTerminatedCall = new SipTerminatedCall();

	// CALL INFO
	private String callId;
	private String localUri;
	private String remoteUri;
	private State state = State.IDLE;

	// CALL DATA
	private final SipUA sipUA;
	private Dialog dialog;
	private STransaction incomingInitiatingRequest;
	private CTransaction outgoingInitiatingRequest;
	private Boolean request2Terminate = false;

	private final LooperThread looperThread = new LooperThread();

	// ////////////////////
	//
	// CONSTRUCTOR
	//
	// ////////////////////

	// Used to create outgoing calls
	SipCall(SipUA sipUA, String fromUri, String toUri) {
		super(sipUA.getContext());
		this.sipUA = sipUA;
		this.callId = UUID.randomUUID().toString();
		this.localUri = fromUri;
		this.remoteUri = toUri;

		looperThread.start();
	}

	// Intended for incoming calls
	SipCall(SipUA sipUA, Dialog dialog) throws KurentoSipException {
		this(sipUA, dialog.getLocalParty().getURI().toString(), dialog
				.getRemoteParty().getURI().toString());
		this.dialog = dialog;
	}

	@Override
	public String getId() {
		return callId;
	}

	@Override
	public String getLocalUri() {
		return localUri;
	}

	@Override
	public String getRemoteUri() {
		return remoteUri;
	}

	@Override
	protected void release() {
		looperThread.quit();
		super.release();
	}

	public Dialog getDialog() {
		return this.dialog;
	}

	// ////////////////////
	//
	// CALL HELPERS
	//
	// ////////////////////

	// FIXME: refactor for new Call API
	private void terminateSync(RejectCode code) {
		request2Terminate = true;
		sipUA.activedCalls.remove(this);

		// Check valid states where a call can be canceled
		if (State.IDLE.equals(state)) {
			// State is idle until INVITE request is sent.
			// DO NOTHING. Cancel must be sent after invite is sent
			log.debug("Request to terminate outgoing call with no INVITE transaction created yet");
		} else if (State.OUTGOING_RINGING.equals(state)) {
			// Hang out an outgoing call after INVITE request is sent and
			// before response is received
			log.debug("Request to terminate pending outgoing call: "
					+ getCallInfo());
			localCallCancelSync();
		} else if (State.INCOMING_RINGING.equals(state)) {
			// TU requested CALL reject
			log.debug("Request to reject incoming call " + getCallInfo()
					+ " with code " + code);
			// This code competes with the remote cancel. First one to execute
			// will cause the other to throw an exception avoiding duplicate
			// events
			// Change state before response to avoid concurrent events with
			// remote CANCEL events
			stateTransitionSync(State.TERMINATED);
			int responseCode = Response.DECLINE;
			if (RejectCode.BUSY.equals(code))
				responseCode = Response.BUSY_HERE;

			try {
				incomingInitiatingRequest.sendResponse(responseCode);
			} catch (KurentoSipException e) {
				callFailedSync(new KurentoException(
						"Unable to send SIP response", e));
			}
			terminatedCallSync(Reason.LOCAL_HANGUP);
		} else if (State.CONFIRMED.equals(state)) {
			// Terminate request after 200 OK response. ACK might still not
			// being received
			log.debug("Request to terminate established call (ACK might still be pending):"
					+ getCallInfo());
			// Change state before request to avoid concurrent BYE requests
			// from local party
			stateTransitionSync(State.TERMINATED);
			try {
				new CBye(sipUA, this);
				terminatedCallSync(Reason.NONE);
			} catch (KurentoSipException e) {
				callFailedSync(new KurentoException(
						"Unable to send BYE request", e));
			}
		} else if (State.TERMINATED.equals(state)) {
			log.info("Call already terminated when hangup request,"
					+ dialog.getDialogId() + ": " + getCallInfo());
		} else {
			log.warn("Bad hangup. Unable to hangup a call (" + getCallInfo()
					+ ") with current state: " + state);
		}
	}

	void terminate(final RejectCode code) {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				terminateSync(code);
			}
		});
	}

	void terminate() {
		terminate(RejectCode.DECLINE);
	}

	private synchronized State getStateTransitionSync() {
		return state;
	}

	private synchronized void stateTransitionSync(State newState) {
		log.debug("--------- SIP CONTEXT STATE TRANSITION ");
		log.debug("| " + getCallInfo() + ": " + state + " ---> " + newState);
		state = newState;
	}

	private String getCallInfo() {
		String arrow;
		if (dialog.isServer())
			arrow = " <<< ";
		else
			arrow = " >>> ";
		return localUri + arrow + remoteUri;
	}

	private void localCallCancelSync() {
		Request cancelReq;
		try {
			cancelReq = outgoingInitiatingRequest.getClientTransaction()
					.createCancel();
		} catch (SipException e) {
			callFailedSync(new KurentoException(
					"Unable to cancel call locally", e));
			return;
		}

		try {
			new CCancel(cancelReq, sipUA);
			// Do not notify. Wait for reception of response 487
		} catch (KurentoSipException e) {
			log.info("Too late to cancel call: " + getCallInfo());
			try {
				new CBye(sipUA, this);
				terminatedCallSync(Reason.LOCAL_HANGUP);
			} catch (KurentoSipException e1) {
				callFailedSync(new KurentoException(
						"Unable to terminate call locally canceled:"
								+ getCallInfo(), e1));
			}
		}
	}

	private void callFailedSync(KurentoException e) {
		log.error("callFailed", e);
		sipUA.getErrorHandler().onCallError(this, e);
		terminatedCallSync(Reason.ERROR);
	}

	private void callFailed(final KurentoException e) {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				callFailedSync(e);
			}
		});
	}

	// ////////////////////
	//
	// TRANSACTION HANDLERS
	//
	// ////////////////////

	private void terminatedCallSync(Reason reason) {
		this.request2Terminate = true;
		sipTerminatedCall.reason = reason;
		stateTransitionSync(State.TERMINATED);
		release();
		sipUA.activedCalls.remove(this);
		sipUA.getCallTerminatedHandler().onTerminated(sipTerminatedCall);
	}

	public void terminatedCall(final Reason reason) {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				terminatedCallSync(reason);
			}
		});
	}

	public void completedCallWithError(String msg) {
		request2Terminate = true;
		completedCall();
	}

	public void remoteRingingCall() {
		sipUA.getCallDialingHandler().onRemoteRinging(sipDialingCall);
	}

	public void callTimeout() {
		callFailed(new KurentoException("SIP protocol timeout"));
	}

	// Use by SInvite to notify an incoming INVITE request. SDP offer is already
	// process and the SDP answer is ready to be sent
	private void incomingCallSync(STransaction incomingTransaction) {
		if (incomingTransaction == null)
			return;

		// Record remote party
		this.incomingInitiatingRequest = incomingTransaction;

		if (request2Terminate) {
			// This condition can verify when a remote CANCEL request is
			// received before the remote SDP offer of incoming INVITE is still
			// being processed
			// Force call cancel and do not signal incoming to the controller
			log.info("Incoming call terminated");
			stateTransitionSync(State.TERMINATED);
			try {
				// Change before transition to avoid concurrent conflict with
				// local reject
				incomingTransaction.sendResponse(Response.REQUEST_TERMINATED);
				// Do not raise events
			} catch (KurentoSipException e) {
				log.warn("Unable to terminate call canceled by remote party", e);
				// Controller doesn't know about this call. Do not signal
				// anything
			}
			release();
		} else {
			// Received INVITE request and no terminate request received in
			// between => Transition to EARLY
			stateTransitionSync(State.INCOMING_RINGING);

			log.info("Incoming call signalled with callId:"
					+ incomingInitiatingRequest.getServerTransaction()
							.getDialog().getCallId());

			// Calculate local and remote uris
			incomingInitiatingRequest = incomingTransaction;

			Address localAddress = incomingTransaction.getServerTransaction()
					.getDialog().getLocalParty();
			Address remoteAddress = incomingTransaction.getServerTransaction()
					.getDialog().getRemoteParty();

			localUri = localAddress.getURI().toString();
			remoteUri = remoteAddress.getURI().toString();

			sipUA.activedCalls.add(this);
			// Notify the incoming call to EndPoint controllers and waits for
			// response (accept or reject)
			sipUA.getCallRingingHandler().onRinging(sipRingingCall);
		}
	}

	public void incomingCall(final STransaction incomingTransaction) {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				incomingCallSync(incomingTransaction);
			}
		});
	}

	// Used by CInvite and SAck to inform when the call set up is completed
	private void completedCallSync() {
		if (request2Terminate) {
			// Call terminate request arrived between 200 OK response and ACK
			// 1.- CANCEL request, either remote or local, arrived after 200 OK
			// 2.- Error found. Normally associated to media
			// 3.- Terminate request due to lack of ACK (symmetric NAT problem)

			if (!State.TERMINATED.equals(state)) {
				// Terminate call not already terminated
				// Use terminated variable as dialog state does not change quick
				// enough
				stateTransitionSync(State.TERMINATED);
				try {
					log.debug("Inmediatelly terminate an already stablished call");
					new CBye(sipUA, this);
					terminatedCallSync(Reason.LOCAL_HANGUP);
				} catch (KurentoSipException e) {
					callFailedSync(new KurentoException(
							"Unable to terminate CALL for dialog: "
									+ dialog.getDialogId(), e));
				}
			}
			return;
		}

		// TODO Make sure the media stack is already created
		stateTransitionSync(State.CONFIRMED);
		sipUA.getCallEstablishedHandler().onEstablished(sipEstablishedCall);

		// Remove reference to the initiating transactions (might be in or out)
		incomingInitiatingRequest = null;
		outgoingInitiatingRequest = null;
	}

	public void completedCall() {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				completedCallSync();
			}
		});
	}

	// Used by SCancel transaction to notify reception of CANCEL request
	private void remoteCallCancelSync() {
		log.info("Request call Cancel from remote peer");
		request2Terminate = true;
		if (State.INCOMING_RINGING.equals(state)) {
			// Cancel received after SDP offer has been process
			// Send now the response and before 200 OK response has been sent
			// (accept)
			stateTransitionSync(State.TERMINATED);
			try {
				incomingInitiatingRequest
						.sendResponse(Response.REQUEST_TERMINATED);
			} catch (KurentoSipException e) {
				log.error("Cannot send request terminated when cancel", e);
			}

			terminatedCallSync(Reason.REMOTE_HANGUP);
		} else {
			// Cancel received before the SDP has been processed. Wait
			// incomingCall event before cancel can be performed
			log.info("Incoming pending request to cancel not yet processed");
		}
	}

	public void remoteCallCancel() {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				remoteCallCancelSync();
			}
		});
	}

	// Use by CInvite to notify when the SDP offer has been generated and
	// request sent. This method is called when dialog state is EARLY
	private void outgoingCallSync(CTransaction outgoingTransaction) {
		if (outgoingTransaction == null)
			return;

		this.dialog = outgoingTransaction.getDialog();
		this.dialog.setApplicationData(this);
		this.outgoingInitiatingRequest = outgoingTransaction;

		stateTransitionSync(State.OUTGOING_RINGING);
		if (request2Terminate) // Call has been canceled while building SDP
			localCallCancelSync();
	}

	public void outgoingCall(final CTransaction outgoingTransaction) {
		looperThread.post(new Runnable() {
			@Override
			public void run() {
				outgoingCallSync(outgoingTransaction);
			}
		});
	}

	// ////////////////
	//
	// SipRingingCall
	//
	// ////////////////

	private class SipRingingCall extends RingingCall {

		@Override
		public String getId() {
			return SipCall.this.getId();
		}

		@Override
		public String getLocalUri() {
			return SipCall.this.getLocalUri();
		}

		@Override
		public String getRemoteUri() {
			return SipCall.this.getRemoteUri();
		}

		private void acceptSync() {
			// Accept only if there are incoming transactions and INCOMING
			// RINIGING
			if (incomingInitiatingRequest == null
					|| !State.INCOMING_RINGING.equals(getStateTransitionSync()))
				callFailedSync(new KurentoException(
						"There is not any incoming call"));

			log.debug("Accept call " + getCallInfo());
			stateTransitionSync(State.CONFIRMED);
			try {
				String localDescription = getLocalDescription();
				if (localDescription == null) {
					callFailedSync(new KurentoException(
							"Local description not set"));
				} else {
					incomingInitiatingRequest.sendResponse(Response.OK,
							localDescription.getBytes());
				}
			} catch (KurentoSipException e) {
				callFailedSync(new KurentoException(
						"Unable to send SIP response", e));
			}
		}

		@Override
		public void accept() {
			looperThread.post(new Runnable() {
				@Override
				public void run() {
					acceptSync();
				}
			});
		}

		@Override
		public void reject(RejectCode code) {
			SipCall.this.terminate(code);
		}

	}

	// ////////////////
	//
	// SipDialingCall
	//
	// ////////////////

	private class SipDialingCall extends DialingCall {

		@Override
		public String getId() {
			return SipCall.this.getId();
		}

		@Override
		public String getLocalUri() {
			return SipCall.this.getLocalUri();
		}

		@Override
		public String getRemoteUri() {
			return SipCall.this.getRemoteUri();
		}

		@Override
		public void cancel() {
			SipCall.this.terminate();
		}

	}

	// ////////////////
	//
	// SipEstablishedCall
	//
	// ////////////////

	private class SipEstablishedCall extends EstablishedCallBase {

		@Override
		public String getId() {
			return SipCall.this.getId();
		}

		@Override
		public String getLocalUri() {
			return SipCall.this.getLocalUri();
		}

		@Override
		public String getRemoteUri() {
			return SipCall.this.getRemoteUri();
		}

		@Override
		public void hangup() {
			SipCall.this.terminate();
		}

	}

	// ////////////////
	//
	// SipTerminatedCall
	//
	// ////////////////

	private class SipTerminatedCall extends TerminatedCall {

		private Reason reason = Reason.NONE;

		@Override
		public String getId() {
			return SipCall.this.getId();
		}

		@Override
		public String getLocalUri() {
			return SipCall.this.getLocalUri();
		}

		@Override
		public String getRemoteUri() {
			return SipCall.this.getRemoteUri();
		}

		@Override
		public Reason getReason() {
			return reason;
		}

	}

}
