/*******************************************************************************
 * Copyright (c) 2009 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.console.internal;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.debug.ui.console.IConsole;
import org.eclipse.debug.ui.console.IConsoleHyperlink;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.IHyperlink;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.console.IOConsoleOutputStream;
import org.eclipse.ui.part.IPageBookViewPage;
import org.tigris.mtoolkit.console.utils.ConstantsDistributor;
import org.tigris.mtoolkit.console.utils.Messages;
import org.tigris.mtoolkit.console.utils.OSGiConsolePlugin;
import org.tigris.mtoolkit.console.utils.Util;
import org.tigris.mtoolkit.console.utils.images.ImageHolder;
import org.tigris.mtoolkit.iagent.DeviceConnectionListener;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.iagent.IAgentException;


public class RemoteConsole extends IOConsole implements IConsole {

	private DeviceConnector connector;
	private ConsoleReader reader;
	private Listener listener = new Listener();
	private Date timestamp;
	private IOConsoleOutputStream output;
	private String name;
	private IProcess process;
	
	public static final String P_DISCONNECTED = "org.tigris.mtoolkit.console.internal.console.disconnected";
	
	public RemoteConsole(DeviceConnector dc, String name, IProcess iProcess) {
		super("", "osgiManagementConsole",
				ImageHolder.getImageDescriptor(ConstantsDistributor.SERVER_ICON_CONNECTED), true);
		this.name = name;
		this.connector = dc;
		this.process = iProcess;
		timestamp = new Date();
		DeviceConnector.addDeviceConnectionListener(listener);
	    setAttribute("mtoolkit.console.connector", connector);
	}
	
	protected void init() {
		super.init();
	}
	
	public ImageDescriptor getImageDescriptor() {
		if (isDisconnected()) {
			return ImageHolder.getImageDescriptor(ConstantsDistributor.SERVER_ICON_DISCONNECTED);
		}
		return ImageHolder.getImageDescriptor(ConstantsDistributor.SERVER_ICON_CONNECTED);
	}
	
	public IPageBookViewPage createPage(IConsoleView view) {
		IPageBookViewPage createPage = super.createPage(view);
		
		Job job = new Job(Messages.redirect_console_output) {
			protected IStatus run(IProgressMonitor monitor) {
				if (connector != null && connector.isActive()) {
					reader = redirectInput();
					output = newOutputStream();
					redirectOutput(output);
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule();

		setName(computeName());
		return createPage;
	}
	

	public void setConsoleName(String name) {
		this.name = name;
		setName(computeName());
	}
	
	private String computeName() {
		String fwName = name;
		String timeStamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(timestamp);
		return (isDisconnected() ? "<disconnected> " : "") + fwName + " [Remote Framework] (" + timeStamp + ")";
	}

	private ConsoleReader redirectInput() {
		if (connector == null)
			return null;
		try {
			return new ConsoleReader(this, connector.getVMManager());
		} catch (IAgentException e) {
			OSGiConsolePlugin.error("Exception while redirecting console input", e);
		}
		return null;
	}
	
	private void redirectOutput(IOConsoleOutputStream output) {
		try {
			if (connector != null) {
				connector.getVMManager().redirectFrameworkOutput(output);
			}
		} catch (IAgentException e) {
			try {
				IStatus status = Util.handleIAgentException(e);
				output.write(NLS.bind("Failed to redirect framework output: {0}", status.getMessage()));
			} catch (IOException e1) {
				OSGiConsolePlugin.error("Exception while writing to console", e1);
			}
			OSGiConsolePlugin.log(Util.handleIAgentException(e));
		}
	}
	
	public void disconnect() {
		DeviceConnector.removeDeviceConnectionListener(listener);
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				setName(computeName());
			}
		});
		if (reader != null)
			reader.dispose();
		if (output != null) {
			if (connector != null && connector.isActive())
				try {
					connector.getVMManager().redirectFrameworkOutput(null);
				} catch (IAgentException e) {
					OSGiConsolePlugin.error("Failed to reset framework output", e);
				}
			try {
				output.close();
			} catch (IOException e) {
			}
		}
		firePropertyChange(this, P_DISCONNECTED, Boolean.FALSE, Boolean.TRUE);
		super.dispose();
	}
	
	public boolean isDisconnected() {
		return connector == null || !connector.isActive();
	}
	
	protected void dispose() {
		disconnect();
		super.dispose();
	}



	private class Listener implements DeviceConnectionListener {
		public void connected(DeviceConnector connector) {
		}

		public void disconnected(DeviceConnector connector) {
			if (connector != null && connector.equals(RemoteConsole.this.connector)) {
				disconnect();
			}
		}
	}

	public IProcess getProcess() {
		return process;
	}

	public IOConsoleOutputStream getStream(String streamIdentifier) {
		return output;
	}
	
    public void addLink(IConsoleHyperlink link, int offset, int length) {
    }

    public void addLink(IHyperlink link, int offset, int length) {
    }

    public void connect(IStreamsProxy streamsProxy) {
    }

    public void connect(IStreamMonitor streamMonitor, String streamIdentifer) {
    }
    
    public IRegion getRegion(IConsoleHyperlink link) {
        return super.getRegion(link);
    }
}