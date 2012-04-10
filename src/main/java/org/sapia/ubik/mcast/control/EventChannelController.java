package org.sapia.ubik.mcast.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.sapia.ubik.concurrent.SynchronizedRef;
import org.sapia.ubik.log.Category;
import org.sapia.ubik.log.Log;
import org.sapia.ubik.mcast.EventChannel.Role;
import org.sapia.ubik.mcast.control.challenge.ChallengeRequest;
import org.sapia.ubik.mcast.control.challenge.ChallengeRequestHandler;
import org.sapia.ubik.mcast.control.heartbeat.DownNotification;
import org.sapia.ubik.mcast.control.heartbeat.DownNotificationHandler;
import org.sapia.ubik.mcast.control.heartbeat.HeartbeatRequest;
import org.sapia.ubik.mcast.control.heartbeat.HeartbeatRequestHandler;
import org.sapia.ubik.mcast.control.heartbeat.PingRequest;
import org.sapia.ubik.mcast.control.heartbeat.PingRequestHandler;
import org.sapia.ubik.util.Clock;


public class EventChannelController {
	
	// --------------------------------------------------------------------------
	
	static class PendingResponseState {
		
		private ControlResponseHandler handler;
		private long 									 requestId;
		private long                   requestTime;
		
		public PendingResponseState(ControlResponseHandler handler, long requestId, long requestTime) {
	    this.handler     = handler;
	    this.requestId   = requestId;
	    this.requestTime = requestTime;
    }
		
		ControlResponseHandler getHandler() {
	    return handler;
    }
		
		long getRequestId() {
	    return requestId;
    }
		
		public long getRequestTime() {
	    return requestTime;
    }
		
	}
	
	// ==========================================================================
	
	private Category log = Log.createCategory(getClass());
	
	private	ControllerConfiguration config;
	private ControllerContext				context;			 
	
	private SynchronizedRef<PendingResponseState> 	      ref 								 = new SynchronizedRef<PendingResponseState>();
	private	Map<String, ControlRequestHandler> 			      requestHandlers 		 = new HashMap<String, ControlRequestHandler>();
	private	Map<String, ControlNotificationHandler> 		  notificationHandlers = new HashMap<String, ControlNotificationHandler>();
	private	Map<String, SynchronousControlRequestHandler> syncRequestHandlers  = new HashMap<String, SynchronousControlRequestHandler>();
	
	public EventChannelController(ControllerConfiguration config, ChannelCallback callback) {
		this(Clock.SystemClock.getInstance(), config, callback);
	}
	
	public EventChannelController(Clock clock, ControllerConfiguration config, ChannelCallback callback) {
		this.config  = config;
		context = new ControllerContext(callback, clock);
		requestHandlers.put(ChallengeRequest.class.getName(), 		 new ChallengeRequestHandler(context));
		requestHandlers.put(HeartbeatRequest.class.getName(), 		 new HeartbeatRequestHandler(context));
		syncRequestHandlers.put(PingRequest.class.getName(), 			 new PingRequestHandler(context));
		notificationHandlers.put(DownNotification.class.getName(), new DownNotificationHandler(context));
	}
	
	ControllerConfiguration getConfig() {
	  return config;
  }
	
	public ControllerContext getContext() {
	  return context;
  }

	public void checkStatus() {

		synchronized(ref) {
			
			// Are there responses pending ? If yes, this check will evaluate to true
  		if(ref.isSet()) {
  			log.debug("There is already a response handler that is active");
  			// checking if pending responses have timed out: if yes, we're discarding the current 
  			// response handler
  			if(context.getClock().currentTimeMillis() - ref.get().getRequestTime() >= config.getResponseTimeout()) {
  				log.debug("Response timeout detected, cancelling handling of responses");
  				ref.get().getHandler().onResponseTimeOut();
  				ref.unset();
  				performControl();
  			}
  		} else {
  			log.debug("No response handler currently set, performing control");
  			performControl();
  		}
		}
		
	}
	
	public synchronized void onResponse(String originNode, ControlResponse response) {
		synchronized(ref) {
  		if(ref.isUnset()) {
  			log.debug("No response handler currently present, discarding response %s", response);
  		} else {
  			PendingResponseState state = ref.get();
  			if(state.getRequestId() != response.getRequestId()) {
  				log.debug(
  						"Request ID does not match response ID (%s vs %s); discarding response %s", 
  						state.getRequestId(), 
  						response.getRequestId(),
  						response
  				);
  			}
  			try {
  				if(state.getHandler().handle(originNode, response)) {
  					ref.set(null);
  				}
  			} catch (RuntimeException e){
  				log.error("Error caught handling response; discarding response handler", e);
  				ref.unset();
  			}
  		}
		}
	}
	
	public synchronized void onRequest(String originNode, ControlRequest request) {
		ControlRequestHandler handler = requestHandlers.get(request.getClass().getName());
		try {
  		if(handler != null) {
  			handler.handle(originNode, request);
  		} else {
  			log.error("No request handler for request %s", request);
  		}
		} finally {
  		// cascading the request
  		request.getTargetedNodes().remove(context.getNode());
  		context.getChannelCallback().sendRequest(request);
		}
	}
	
	public synchronized SynchronousControlResponse onSynchronousRequest(String originNode, SynchronousControlRequest request) {
		SynchronousControlRequestHandler handler = syncRequestHandlers.get(request.getClass().getName());
		if(handler != null) {
			return handler.handle(originNode, request);
		} else {
			log.error("No request handler for request %s", request);
			return null;
		}
	}
	
	public synchronized void onNotification(String originNode, ControlNotification notification) {
		ControlNotificationHandler handler = notificationHandlers.get(notification.getClass().getName());
		try {
			if(handler != null) {
  			handler.handle(originNode, notification);
  		} else {
  			log.error("No notification handler for notification %s; got: %s", notification, notificationHandlers);
  		}
		} finally {
  		// cascading the notification
  		notification.getTargetedNodes().remove(context.getNode());
  		context.getChannelCallback().sendNotification(notification);
		}
	}
	
	private void performControl() { 
		log.debug("Current role is %s", context.getRole());		
		switch(context.getRole()) {
		
			// Role is currently undefined, proceeding to challenge
			case UNDEFINED:
				doTriggerChallenge(false);
  		break;
  			
  	  // ----------------------------------------------------------------------

			// Challenge is on-going: this node has deemed it's the master, it has sent
  		// a ChallengeRequest and is waiting for the corresponding ChallengeResponses.
  		// We're not doing anyting until the current response handler completes.
  		case MASTER_CANDIDATE:
  		break;				
  			
  	  // ----------------------------------------------------------------------
  			
  		// This is the master: it is sending a heartbeat request and creating a matching
  		// response handler.
  		case MASTER:
  			log.debug("Sending heartbeat request");
  			ControlRequest 			 	 heartbeatRq 			= ControlRequestFactory.createHeartbeatRequest(context);
  			ControlResponseHandler heartbeatHandler = ControlResponseHandlerFactory.createHeartbeatResponseHandler(
  					context, new HashSet<String>(context.getChannelCallback().getNodes())
  			);
  			ref.set(new PendingResponseState(heartbeatHandler, heartbeatRq.getRequestId(), context.getClock().currentTimeMillis()));
  			context.heartbeatRequestSent();
  			context.getChannelCallback().sendRequest(heartbeatRq);
  		break;
  		
  		// ----------------------------------------------------------------------
  
  		// This node is a slave: has it received a heartbeat request "lately" ? If no, 
  		// we're triggering a challenge: the master may be down.
  		default: // SLAVE
  			if(context.getClock().currentTimeMillis() - context.getLastHeartbeatRequestReceivedTime() >= config.getHeartbeatTimeout()) {
  				log.debug("Heartbeat request has not been received in timely manner since last time, triggering challenge");
  				doTriggerChallenge(true);
  			}
		}
	}
	
	private void doTriggerChallenge(boolean force) {
		List<String> nodes = new ArrayList<String>(context.getChannelCallback().getNodes());
		
		// Sorting the node identifiers and comparing them to this node's. In the end,
		// the node that comes "first" becomes the master.
		Collections.sort(nodes);
		if(nodes.size() > 0) {			
			log.debug("Found %s other node(s): %s", nodes.size(), nodes);
			if(force || context.getNode().compareTo(nodes.get(0)) <= 0) {
				// Master role not yet confirmed, upgrading to candidate for now.
				context.setRole(Role.MASTER_CANDIDATE);
			}
		// Lonely node: automatically becomes the master
		} else {
			log.debug("No other nodes found, setting self to candidate for master", nodes.size());
			context.setRole(Role.MASTER_CANDIDATE);		
		}
		
		if(context.getRole() == Role.MASTER_CANDIDATE) {
			log.debug("Node %s triggering challenge", context.getNode());			
			ControlRequest 			 	 challengeRq   		= ControlRequestFactory.createChallengeRequest(context);
			ControlResponseHandler challengeHandler = ControlResponseHandlerFactory.createChallengeResponseHandler(
					context, context.getChannelCallback().getNodes()
			);
			ref.set(new PendingResponseState(challengeHandler, challengeRq.getRequestId(), context.getClock().currentTimeMillis()));
			context.challengeRequestSent();
			context.getChannelCallback().sendRequest(challengeRq);
		} else {
			log.debug("Node %s is setting itelf to slave", context.getNode());			
			context.setRole(Role.SLAVE);
		}
	}
	
}