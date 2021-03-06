package org.tigris.mtoolkit.iagent.internal;

import java.util.Dictionary;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tigris.mtoolkit.iagent.ApplicationManager;
import org.tigris.mtoolkit.iagent.IAgentErrors;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.Error;
import org.tigris.mtoolkit.iagent.RemoteApplication;
import org.tigris.mtoolkit.iagent.event.RemoteApplicationEvent;
import org.tigris.mtoolkit.iagent.event.RemoteApplicationListener;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;
import org.tigris.mtoolkit.iagent.pmp.EventListener;
import org.tigris.mtoolkit.iagent.pmp.RemoteObject;
import org.tigris.mtoolkit.iagent.spi.ConnectionEvent;
import org.tigris.mtoolkit.iagent.spi.ConnectionListener;
import org.tigris.mtoolkit.iagent.spi.ConnectionManager;
import org.tigris.mtoolkit.iagent.spi.DeviceConnectorSpi;
import org.tigris.mtoolkit.iagent.spi.IAgentManager;
import org.tigris.mtoolkit.iagent.spi.MethodSignature;
import org.tigris.mtoolkit.iagent.spi.PMPConnection;

public class ApplicationManagerImpl implements ApplicationManager, IAgentManager, EventListener, ConnectionListener {

	private static final String SYNCH_APPLICATION_EVENT = "synch_application_event";
	private static final String EVENT_APPLICATION_ID_KEY = "application_id";
	private static final String EVENT_TYPE_KEY = "type";

	private final MethodSignature GET_APPLICATIONS = new MethodSignature("getApplications");
	private final MethodSignature START = new MethodSignature("start", new Class[] { String.class, Map.class });
	private final MethodSignature STOP = new MethodSignature("stop", String.class);
	private final MethodSignature GET_STATE = new MethodSignature("getState", String.class);
	private final MethodSignature GET_PROPERTIES = new MethodSignature("getProperties", String.class); 

	private DeviceConnectorSpi connectorSpi;
	private List applicationListeners = new LinkedList();
	private boolean addedConnectionListener;

	private RemoteObject getApplicationAdmin() throws IAgentException {
		PMPConnection connection = (PMPConnection) connectorSpi.getConnectionManager().createConnection(
				ConnectionManager.PMP_CONNECTION);
		RemoteObject applicationAdmin = connection.getRemoteApplicationAdmin();
		return applicationAdmin;
	}

	public void addRemoteApplicationListener(RemoteApplicationListener listener) throws IAgentException {
		debug("[addRemoteApplicaionListener] >>> listener: " + listener);
		synchronized (this) {
			if (!addedConnectionListener) {
				connectorSpi.getConnectionManager().addConnectionListener(this);
				addedConnectionListener = true;
				debug("[addRemoteBundleListener] Connection listener added");
			}
		}
		synchronized (applicationListeners) {
			if (!applicationListeners.contains(listener)) {
				PMPConnection connection = (PMPConnection) connectorSpi.getConnectionManager().getActiveConnection(
						ConnectionManager.PMP_CONNECTION);
				if (connection != null) {
					debug("[addRemoteApplicationListener] PMP connection is available, add event listener");
					connection.addEventListener(this, new String[] { SYNCH_APPLICATION_EVENT });
				}
				applicationListeners.add(listener);
			} else {
				debug("[addRemoteApplicationListener] Listener already present");
			}
		}
	}

	public RemoteApplication[] listApplications() throws IAgentException {
		debug("[listApplications] >>>");
		String[] applicationIDs = (String[]) GET_APPLICATIONS.call(getApplicationAdmin());
		if (applicationIDs == null) {
			String msg = "listApplications() must not return null array. There is a problem with the transport.";
			error("[listApplications] " + msg);
			throw new IAgentException(msg, IAgentErrors.GENERAL_ERROR);
		}
		RemoteApplication[] applications = new RemoteApplication[applicationIDs.length];
		debug("[listApplications] Returned applications list: " + DebugUtils.convertForDebug(applicationIDs));
		for (int i = 0; i < applicationIDs.length; i++) {
			applications[i] = new RemoteApplicationImpl(this, applicationIDs[i]);
		}
		return applications;
	}

	public void removeRemoteApplicationListener(RemoteApplicationListener listener) throws IAgentException {
		debug("[removeApplicationListener] >>> listener: " + listener);
		synchronized (applicationListeners) {
			if (applicationListeners.contains(listener)) {
				applicationListeners.remove(listener);
				if (applicationListeners.size() == 0) {
					debug("[removeRemoteApplicationListener] No more listeners in the list, try to remove PMP event listener");
					PMPConnection connection = (PMPConnection) connectorSpi.getConnectionManager().getActiveConnection(
							ConnectionManager.PMP_CONNECTION);
					if (connection != null) {
						debug("[removeRemoteApplicationListener] PMP connection is available, remove event listener");
						connection.removeEventListener(this, new String[] { SYNCH_APPLICATION_EVENT });
					}
				}
			} else {
				debug("[removeRemoteApplicationListener] Listener not found in the list");
			}
		}
	}

	private void fireApplicationEvent(String applicationID, int type) {
		debug("[fireApplicationEvent] >>> applicationId: " + applicationID + "; type: " + type);
		RemoteApplicationListener[] listeners;
		synchronized (applicationListeners) {
			if (applicationListeners.size() != 0) {
				listeners = (RemoteApplicationListener[]) applicationListeners
						.toArray(new RemoteApplicationListener[applicationListeners.size()]);
			} else {
				return;
			}
		}
		RemoteApplication application = new RemoteApplicationImpl(this, applicationID);
		RemoteApplicationEvent event = new RemoteApplicationEvent(application, type);
		debug("[fireApplicationEvent] " + listeners.length + " listeners found.");
		for (int i = 0; i < listeners.length; i++) {
			RemoteApplicationListener listener = listeners[i];
			try {
				debug("[fireApplicationEvent] deliver event: " + event + " to listener: " + listener);
				listener.applicationChanged(event);
			} catch (Throwable e) {
				error("[fireApplicationEvent] Failed to deliver event to " + listener, e);
			}
		}
	}

	public void event(Object event, String eventType) {
		try {
			debug("[event] >>> event: " + event + "; type: " + eventType);
			if (SYNCH_APPLICATION_EVENT.equals(eventType)) {
				Dictionary eventProps = (Dictionary) event;
				if (eventProps == null)
					return;
				int type = ((Integer) eventProps.get(EVENT_TYPE_KEY)).intValue();
				String applicationId = (String) eventProps.get(EVENT_APPLICATION_ID_KEY);
				fireApplicationEvent(applicationId, type);
			}
		} catch (Throwable e) {
			error("[event] Failed to process PMP event: " + event + "; type: "
					+ eventType, e);
		}
	}

	public void connectionChanged(ConnectionEvent event) {
		debug("[connectionChanged] >>> event: " + event);
		if (event.getType() == ConnectionEvent.CONNECTED
				&& event.getConnection().getType() == ConnectionManager.PMP_CONNECTION) {
			debug("[connectionChanged] New PMP connection created, restore event listeners");
			synchronized (applicationListeners) {
				PMPConnection connection = (PMPConnection) event.getConnection();
				if (applicationListeners.size() > 0) {
					debug("[connectionChanged] Restoring application listeners...");
					try {
						connection.addEventListener(this, new String[] { SYNCH_APPLICATION_EVENT });
					} catch (IAgentException e) {
						error("[connectionChanged] Failed to add event listener to PMP connection", e);
					}
				}
			}
		}
	}

	public void dispose() {
		try {
			synchronized (applicationListeners) {
				applicationListeners.clear();
				PMPConnection connection = (PMPConnection) connectorSpi.getConnectionManager().getActiveConnection(
						ConnectionManager.PMP_CONNECTION);
				if (connection != null) {
					debug("[removeListeners] PMP connection is available, remove event listener for synchronous application events...");
					connection.removeEventListener(this, new String[] { SYNCH_APPLICATION_EVENT });
				}
				debug("[removeListeners] application listeners removed");
			}
		} catch (IAgentException e) {
			error("[dispose] Exception while disposing application manager", e);
		}
	}

	public void init(DeviceConnectorSpi connector) {
		this.connectorSpi = connector;
	}

	public void startApplication(String applicationID, Map properties) throws IAgentException {
		Object result = START.call(getApplicationAdmin(), new Object[] { applicationID, properties });
		if (result instanceof Error) {
			Error err = (Error) result;
			throw new IAgentException("Failed to start application" + (err.getMessage() != null ? ": " + err.getMessage() : ""), err.getCode());
		}
	}

	public void stopApplication(String applicationID) throws IAgentException {
		Object result = STOP.call(getApplicationAdmin(), applicationID);
		if (result instanceof Error) {
			Error err = (Error) result;
			throw new IAgentException("Failed to stop application" + (err.getMessage() != null ? ": " + err.getMessage() : ""), err.getCode());
		}
	}

	public String getApplicationState(String applicationID) throws IAgentException {
		return (String) GET_STATE.call(getApplicationAdmin(), applicationID);
	}
	
	public Map getApplicationProperties(String applicationId) throws IAgentException {
		Object result = GET_PROPERTIES.call(getApplicationAdmin(), applicationId);
		if (result instanceof Error) {
			throw new IAgentException((Error) result);
		}
		return (Map) result;
	}

	private final void debug(String message) {
		DebugUtils.debug(this, message);
	}

	private final void error(String message) {
		DebugUtils.error(this, message);
	}

	private final void error(String message, Throwable e) {
		DebugUtils.error(this, message, e);
	}
}
