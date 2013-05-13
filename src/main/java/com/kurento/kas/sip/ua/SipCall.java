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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sip.Dialog;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.AudioTrack;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.PeerConnection.SignalingState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import com.kurento.kas.sip.transaction.CBye;
import com.kurento.kas.sip.transaction.CCancel;
import com.kurento.kas.sip.transaction.CTransaction;
import com.kurento.kas.sip.transaction.STransaction;
import com.kurento.kas.sip.ua.Preferences.Direction;
import com.kurento.kas.ua.Call;
import com.kurento.kas.ua.KurentoException;

public class SipCall implements Call {

	protected static final Logger log = LoggerFactory.getLogger(SipCall.class
			.getSimpleName());

	// CALL INFO
	private String localUri;
	private String remoteUri;
	private State state = State.IDLE;
	private TerminateReason reason = TerminateReason.NONE;

	// CALL DATA
	private SipUA sipUA;
	private Dialog dialog;
	private STransaction incomingInitiatingRequest;
	private CTransaction outgoingInitiatingRequest;
	private Boolean request2Terminate = false;

	// MEDIA DATA
	private PeerConnectionFactory peerConnectionFactory;
	private PeerConnection peerConnection;

	private MediaStream localStream;
	private MediaStream remoteStream;

	private AudioTrack audioTrack;
	private VideoTrack videoTrack;
	private VideoCapturer capturer = null;

	private Set<CreateSdpOfferObserver> createSdpOfferObservers = new HashSet<CreateSdpOfferObserver>();
	private Set<CreateSdpAnswerObserver> createSdpAnswerObservers = new HashSet<CreateSdpAnswerObserver>();

	// ////////////////////
	//
	// CONSTRUCTOR
	//
	// ////////////////////

	// Used to create outgoing calls
	protected SipCall(SipUA sipUA, String fromUri, String toUri)
			throws KurentoSipException {
		this.sipUA = sipUA;
		this.localUri = fromUri;
		this.remoteUri = toUri;
		createPeerConnection();
	}

	// Intended for incoming calls
	protected SipCall(SipUA sipUA, Dialog dialog) throws KurentoSipException {
		this(sipUA, dialog.getLocalParty().getURI().toString(), dialog
				.getRemoteParty().getURI().toString());
		this.dialog = dialog;
	}

	public Dialog getDialog() {
		return this.dialog;
	}

	// ////////////////////
	//
	// CALL API
	//
	// ////////////////////

	@Override
	public void accept() throws KurentoException {
		// Accept only if there are incoming transactions
		log.debug("Accept Call: " + getCallInfo());

		if (incomingInitiatingRequest == null) {
			return; // Silently
		}

		// Accept: only possible for INCOMING RINIGING call
		if (State.INCOMING_RINGING.equals(state)) {
			stateTransition(State.CONFIRMED);
			try {
				incomingInitiatingRequest.sendResponse(Response.OK,
						peerConnection.getLocalDescription().description
								.getBytes());
				incomingInitiatingRequest = null;
			} catch (KurentoSipException e) {
				// SIP failures are noti
				callFailed(new KurentoException("Unable to send SIP response",
						e));
			}
		}
	}

	@Override
	public void hangup() {
		hangup(RejectCode.DECLINE);
	}

	@Override
	public void hangup(RejectCode code) {
		// Label this call to be terminated as soon as possible
		request2Terminate = true;

		// Check valid states where a call can be canceled
		if (State.IDLE.equals(state)) {
			// State is idle until INVITE request is sent.
			// DO NOTHING. Cancel must be sent after invite is sent
			log.debug("Request to terminate outgoing call with no INVITE transaction created yet: "
					+ getCallInfo());
		} else if (State.OUTGOING_RINGING.equals(state)) {
			// Hang out an outgoing call after INVITE request is sent and
			// before response is received
			log.debug("Request to terminate pending outgoing call: "
					+ getCallInfo());
			// Send cancel request
			localCallCancel();
		} else if (State.INCOMING_RINGING.equals(state)) {
			// TU requested CALL reject
			log.debug("Request to reject incoming call: " + getCallInfo());
			// This code competes with the remote cancel. First one to execute
			// will cause the other to throw an exception avoiding duplicate
			// events
			// Change state before response to avoid concurrent events with
			// remote CANCEL events
			stateTransition(State.TERMINATED);
			log.debug("Request to reject a call with code: " + code);
			int responseCode;
			if (RejectCode.BUSY.equals(code)) {
				responseCode = Response.BUSY_HERE;
			} else {
				responseCode = Response.DECLINE;
			}
			try {
				incomingInitiatingRequest.sendResponse(responseCode, null);
			} catch (KurentoSipException e) {
				callFailed(new KurentoException("Unable to send SIP response",
						e));
			}
			rejectCall();
		} else if (State.CONFIRMED.equals(state)) {
			// Terminate request after 200 OK response. ACK might still not
			// being received
			log.debug("Request to terminate established call (ACK might still be pending):"
					+ getCallInfo());
			// Change state before request to avoid concurrent BYE requests
			// from local party
			stateTransition(State.TERMINATED);
			try {
				new CBye(sipUA, this);
				terminatedCall(TerminateReason.NONE);
			} catch (KurentoSipException e) {
				callFailed(new KurentoException("Unable to send BYE request", e));
			}
		} else if (State.TERMINATED.equals(state)) {
			log.info("Call already terminated when hangup request,"
					+ dialog.getDialogId() + ": " + getCallInfo());
		}

		// Do not accept call to this method
		else {
			log.warn("Bad hangup. Unable to hangup a call (" + getCallInfo()
					+ ") with current state: " + state);
		}
	}

	@Override
	public MediaStream getLocalStream() {
		return localStream;
	}

	@Override
	public MediaStream getRemoteStream() {
		return remoteStream;
	}

	// ////////////////////
	//
	// GETTERS & SETTERS
	//
	// ////////////////////

	@Override
	public String getId() {
		return null;
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
	public State getState() {
		return state;
	}

	@Override
	public TerminateReason getReason() {
		return reason;
	}

	// ////////////////////
	//
	// CALL HELPERS
	//
	// ////////////////////

	private synchronized void stateTransition(State newState) {
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

	private void release() {
		if (peerConnection != null) {
			peerConnection.close();
			// peerConnection.dispose(); //FIXME: test fails when detach thread
			peerConnection = null;
			localStream = null;
			remoteStream = null;
		}

		if (capturer != null) {
			capturer.dispose();
			capturer = null;
		}

		// FIXME: fails
		// if (peerConnectionFactory != null) {
		// peerConnectionFactory.dispose();
		// peerConnectionFactory = null;
		// }
	}

	private void localCallCancel() {
		log.debug("localCallCancel");
		Request cancelReq;
		// Create cancel request
		try {
			cancelReq = outgoingInitiatingRequest.getClientTransaction()
					.createCancel();
		} catch (SipException e) {
			callFailed(new KurentoException("Unable to cancel call locally", e));
			return;
		}

		// Send cancel request
		try {
			new CCancel(cancelReq, sipUA);
			// Do not notify. Wait for reception of response 487
		} catch (KurentoSipException e) {
			log.info("Too late to cancel call: " + getCallInfo());
			// Try BYE
			try {
				new CBye(sipUA, this);
				terminatedCall(TerminateReason.LOCAL_HANGUP);
			} catch (KurentoSipException e1) {
				callFailed(new KurentoException(
						"Unable to terminate call locally canceled:"
								+ getCallInfo(), e1));
			}
		}
	}

	private void rejectCall() {
		terminatedCall(TerminateReason.LOCAL_HANGUP);
	}

	private void callFailed(KurentoException e) {
		log.error("callFailed", e);
		sipUA.getErrorHandler().onCallError(this, e);
		terminatedCall(TerminateReason.ERROR);
	}

	// ////////////////////
	//
	// TRANSACTION SERVICES
	//
	// ////////////////////

	public void addCreateSdpOfferObserver(CreateSdpOfferObserver observer) {
		createSdpOfferObservers.add(observer);
	}

	public void removeCreateSdpOfferObserver(CreateSdpOfferObserver observer) {
		createSdpOfferObservers.remove(observer);
	}

	public void createSdpOffer(final CreateSdpOfferObserver observer) {
		MediaConstraints constraints = new MediaConstraints();

		// FIXME: if OfferToReceiveVideo is false, native library crashes when
		// the thread is detached
		// Preferences pref = sipUA.getPreferences();
		// Direction audioDirection = pref.getAudioDirection();
		// if (Direction.SENDRECV.equals(audioDirection)
		// || Direction.RECVONLY.equals(audioDirection))
		// constraints.mandatory.add(new MediaConstraints.KeyValuePair(
		// "OfferToReceiveAudio", "true"));
		// else
		// constraints.mandatory.add(new MediaConstraints.KeyValuePair(
		// "OfferToReceiveAudio", "false"));
		//
		// Direction videoDirection = pref.getVideoDirection();
		// if (Direction.SENDRECV.equals(videoDirection)
		// || Direction.RECVONLY.equals(videoDirection))
		// constraints.mandatory.add(new MediaConstraints.KeyValuePair(
		// "OfferToReceiveVideo", "true"));
		// else
		// constraints.mandatory.add(new MediaConstraints.KeyValuePair(
		// "OfferToReceiveVideo", "false"));

		constraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveAudio", "true"));
		constraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveVideo", "true"));

		peerConnection.createOffer(new SdpObserver() {
			@Override
			public void onSuccess(SessionDescription sdp) {
				final String description = sdp.description;
				log.debug("createOffer onSuccess. sdp: " + description);
				peerConnection.setLocalDescription(new SdpObserver() {
					@Override
					public void onSuccess(SessionDescription sdp) {
						log.debug("setLocalDescription onSuccess. sdp: "
								+ sdp.description);
					}

					@Override
					public void onSuccess() {
						log.debug("setLocalDescription onSuccess");
					}

					@Override
					public void onFailure(String error) {
						log.debug("setLocalDescription onFailure: " + error);
						observer.onError(new KurentoException(error));
					}
				}, sdp);
			}

			@Override
			public void onSuccess() {
				log.debug("createOffer onSuccess");
			}

			@Override
			public void onFailure(String error) {
				log.debug("createOffer onFailure: " + error);
				observer.onError(new KurentoException(error));
			}
		}, constraints);
	}

	public void addCreateSdpAnswerObserver(CreateSdpAnswerObserver observer) {
		createSdpAnswerObservers.add(observer);
	}

	public void removeCreateSdpAnswerObserver(CreateSdpAnswerObserver observer) {
		createSdpAnswerObservers.remove(observer);
	}

	public void createSdpAnswer(String sdpOffer,
			final CreateSdpAnswerObserver observer) {
		SessionDescription sdp = new SessionDescription(
				SessionDescription.Type.OFFER, sdpOffer);
		peerConnection.setRemoteDescription(new SdpObserver() {
			@Override
			public void onSuccess(SessionDescription sdp) {
				log.debug("setRemoteDescription onSuccess. sdp: " + sdp);
			}

			@Override
			public void onSuccess() {
				log.debug("setRemoteDescription onSuccess");
				MediaConstraints constraints = new MediaConstraints();
				peerConnection.createAnswer(new SdpObserver() {
					@Override
					public void onSuccess(SessionDescription sdp) {
						log.debug("createAnswer onSuccess. sdp: "
								+ sdp.description);
						peerConnection.setLocalDescription(new SdpObserver() {
							@Override
							public void onSuccess(SessionDescription sdp) {
								log.debug("setLocalDescription onSuccess. sdp: "
										+ sdp.description);
							}

							@Override
							public void onSuccess() {
								log.debug("setLocalDescription onSuccess");
							}

							@Override
							public void onFailure(String error) {
								log.debug("setLocalDescription onFailure: "
										+ error);
								observer.onError(new KurentoException(error));
							}
						}, sdp);
					}

					@Override
					public void onSuccess() {
						log.debug("createAnswer onSuccess");
					}

					@Override
					public void onFailure(String error) {
						log.debug("createAnswer onFailure: " + error);
						observer.onError(new KurentoException(error));
					}

				}, constraints);
			}

			@Override
			public void onFailure(String error) {
				log.debug("setRemoteDescription onFailure: " + error);
				observer.onError(new KurentoException(error));
			}

		}, sdp);
	}

	// ////////////////////
	//
	// TRANSACTION HANDLERS
	//
	// ////////////////////

	public void terminatedCall(TerminateReason reason) {
		this.request2Terminate = true;
		this.reason = reason;
		stateTransition(State.TERMINATED);
		release();
		sipUA.getCallTerminatedHandler().onTerminate(this);
	}

	public void completedCallWithError(String msg) {
		request2Terminate = true;
		completedCall();
	}

	public void callError(String msg) {
		terminatedCall(TerminateReason.ERROR);
	}

	public void LocalCallCancel() {
		terminatedCall(TerminateReason.LOCAL_HANGUP);
	}

	public void remoteCallBusy() {
		terminatedCall(TerminateReason.BUSY);
	}

	public void remoteCallReject() {
		terminatedCall(TerminateReason.REMOTE_HANGUP);
	}

	public void remoteRingingCall() {
		sipUA.getCallDialingHandler().onRemoteRinging(this);
	}

	public void userNotFound() {
		terminatedCall(TerminateReason.USER_NOT_FOUND);
	}

	public void unsupportedMediaType() {
		terminatedCall(TerminateReason.ERROR);
	}

	public void unsupportedCode() {
		terminatedCall(TerminateReason.ERROR);
	}

	public void callTimeout() {
		callFailed(new KurentoException("SIP protocol timeout"));
	}

	// Use by SInvite to notify an incoming INVITE request. SDP offer is already
	// process and the SDP answer is ready to be sent
	public void incomingCall(STransaction incomingTransaction) {
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
			stateTransition(State.TERMINATED);
			try {
				// Change before transition to avoid concurrent conflict with
				// local reject
				incomingTransaction.sendResponse(Response.REQUEST_TERMINATED,
						null);
				// Do not raise events
			} catch (KurentoSipException e) {
				log.warn("Unable to terminate call canceled by remote party", e);
				// Controller doesn't know about this call. Do not signall
				// anything
			}
			release();
		} else {
			// Received INVITE request and no terminate request received in
			// between => Transition to EARLY
			stateTransition(State.INCOMING_RINGING);

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

			// Notify the incoming call to EndPoint controllers and waits for
			// response (accept or reject)
			sipUA.getCallRingingHandler().onRinging(this);
		}
	}

	// Used by CInvite and SAck to inform when the call set up is completed
	public void completedCall() {
		if (request2Terminate) {
			// Call terminate request arrived between 200 OK response and ACK
			// 1.- CANCEL request, either remote or local, arrived after 200 OK
			// 2.- Error found. Normally associated to media
			// 3.- Terminate request due to lack of ACK (symmetric NAT problem)

			if (!State.TERMINATED.equals(state)) {
				// Terminate call not already terminated
				// Use terminated variable as dialog state does not change quick
				// enough
				stateTransition(State.TERMINATED);
				try {
					log.debug("Inmediatelly terminate an already stablished call");
					new CBye(sipUA, this);
					terminatedCall(TerminateReason.LOCAL_HANGUP);
				} catch (KurentoSipException e) {
					callFailed(new KurentoException(
							"Unable to terminate CALL for dialog: "
									+ dialog.getDialogId(), e));
				}
			}
			return;
		}

		// TODO Make sure the media stack is already created
		stateTransition(State.CONFIRMED);
		sipUA.getCallEstablishedHandler().onEstablished(this);

		// Remove reference to the initiating transactions (might be in or out)
		incomingInitiatingRequest = null;
		outgoingInitiatingRequest = null;
	}

	// Used by SCancel transaction to notify reception of CANCEL request
	public void remoteCallCancel() throws KurentoSipException {
		log.info("Request call Cancel from remote peer");
		request2Terminate = true;
		if (State.INCOMING_RINGING.equals(state)) {
			// Cancel received after SDP offer has been process
			// Send now the response and before 200 OK response has been sent
			// (accept)
			stateTransition(State.TERMINATED);
			incomingInitiatingRequest.sendResponse(Response.REQUEST_TERMINATED,
					null);
			LocalCallCancel();
			// Remove reference to the initiating request
			// incomingInitiatingRequest = null;
		} else {
			// Cancel received before the SDP has been processed. Wait
			// incomingCall event before cancel can be performed
			log.info("Incoming pending request to cancel not yet processed");
		}
	}

	// Use by CInvite to notify when the SDP offer has been generated and
	// request sent. This method is called when dialog state is EARLY
	public void outgoingCall(CTransaction outgoingTransaction) {
		if (outgoingTransaction == null)
			return;

		this.dialog = outgoingTransaction.getDialog();
		this.dialog.setApplicationData(this);
		this.outgoingInitiatingRequest = outgoingTransaction;

		stateTransition(State.OUTGOING_RINGING);
		if (request2Terminate) {
			// Call has been canceled while building SDP
			localCallCancel();
		}
	}

	// ////////////////////
	//
	// MEDIA MANAGEMENT
	//
	// ////////////////////

	public PeerConnection getPeerConnection() {
		return peerConnection;
	}

	private void createPeerConnection() {
		if (peerConnection == null) {
			Preferences pref = sipUA.getPreferences();
			peerConnectionFactory = new PeerConnectionFactory();

			String stunServerAddress = pref.getStunServerAdress();
			int stunServerPort = pref.getStunServerPort();
			String stunServerPassword = pref.getStunServerPassword();

			List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();
			if (!"".equals(stunServerAddress)) {
				log.debug("stun server: " + "stun:" + stunServerAddress + ":"
						+ stunServerPort);
				PeerConnection.IceServer iceServer = new PeerConnection.IceServer(
						"stun:" + stunServerAddress + ":" + stunServerPort,
						stunServerPassword);
				iceServers.add(iceServer);
			}

			peerConnection = peerConnectionFactory.createPeerConnection(
					iceServers, new MediaConstraints(),
					new PeerConnection.Observer() {
						@Override
						public void onSignalingChange(SignalingState newState) {
							log.debug("peerConnection onSignalingChange: "
									+ newState);
						}

						@Override
						public void onRemoveStream(MediaStream stream) {
							log.debug("peerConnection onRemoveStream");
						}

						@Override
						public void onIceGatheringChange(
								IceGatheringState newState) {
							log.debug("peerConnection onIceGatheringChange: "
									+ newState);
							if (IceGatheringState.COMPLETE.equals(newState)) {
								for (CreateSdpOfferObserver o : createSdpOfferObservers)
									o.onSdpOfferCreated(peerConnection
											.getLocalDescription().description);
								for (CreateSdpAnswerObserver o : createSdpAnswerObservers)
									o.onSdpAnswerCreated(peerConnection
											.getLocalDescription().description);
							}

						}

						@Override
						public void onIceConnectionChange(
								IceConnectionState newState) {
							log.debug("peerConnection onIceConnectionChange: "
									+ newState);
						}

						@Override
						public void onIceCandidate(IceCandidate candidate) {
							log.debug("peerConnection onIceCandidate: "
									+ candidate.sdp);
						}

						@Override
						public void onError() {
							log.debug("peerConnection onError");
						}

						@Override
						public void onAddStream(MediaStream stream) {
							log.debug("peerConnection onAddStream");
							remoteStream = stream;
						}
					});

			localStream = peerConnectionFactory
					.createLocalMediaStream("ARDAMS");

			// TODO: manage if audioDirection and videoDirection both are
			// INACTIVE because the SDP is not generated
			Direction audioDirection = pref.getAudioDirection();
			log.debug("audioDirection: " + audioDirection);
			if (Direction.SENDRECV.equals(audioDirection)
					|| Direction.SENDONLY.equals(audioDirection)) {
				audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0");
				localStream.addTrack(audioTrack);
			}

			Direction videoDirection = pref.getVideoDirection();
			log.debug("videoDirection: " + videoDirection);
			if (Direction.SENDRECV.equals(videoDirection)
					|| Direction.SENDONLY.equals(videoDirection)) {

				if (pref.isFrontCamera()) {
					log.debug("create front camera");
					capturer = VideoCapturer
							.create("Camera 1, Facing front, Orientation 270");
				}

				if (capturer == null) {
					log.debug("create back camera");
					capturer = VideoCapturer
							.create("Camera 0, Facing back, Orientation 90");
				}

				if (capturer != null) {
					VideoSource videoSource = peerConnectionFactory
							.createVideoSource(capturer, new MediaConstraints());
					videoTrack = peerConnectionFactory.createVideoTrack(
							"ARDAMSv0", videoSource);
					localStream.addTrack(videoTrack);
				}
			}

			peerConnection.addStream(localStream, new MediaConstraints());
		}
	}

	public static interface CreateSdpOfferObserver {
		public void onSdpOfferCreated(String sdp);

		public void onError(KurentoException exception);
	}

	public static interface CreateSdpAnswerObserver {
		public void onSdpAnswerCreated(String sdp);

		public void onError(KurentoException exception);
	}

}
