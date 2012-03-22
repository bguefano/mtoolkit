/**
 * 
 */
package org.tigris.mtoolkit.osgimanagement.internal.browser.logic;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteBundle;
import org.tigris.mtoolkit.osgimanagement.Util;
import org.tigris.mtoolkit.osgimanagement.internal.Messages;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.Bundle;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.FrameworkImpl;
import org.tigris.mtoolkit.osgimanagement.internal.preferences.FrameworkPreferencesPage;

public class StartBundleOperation extends RemoteBundleOperation {
	
	public StartBundleOperation(String bName, Bundle bundle) {
		super(Messages.start_bundle + " " + bName, bundle);
	}

	protected IStatus doOperation(IProgressMonitor monitor) throws IAgentException {
		RemoteBundle rBundle = getBundle().getRemoteBundle();
		int flags =  FrameworkPreferencesPage.isActivationPolicyEnabled() ? org.osgi.framework.Bundle.START_ACTIVATION_POLICY : 0;
		boolean forceRefresh = false; 
		switch (getBundle().getState()) {
		case org.osgi.framework.Bundle.ACTIVE :
			forceRefresh = true;
		case org.osgi.framework.Bundle.STARTING :
			flags = 0;
		}
		rBundle.start(flags);
		if (forceRefresh) {
			getBundle().update();
		}
		if (rBundle.getState() == org.osgi.framework.Bundle.RESOLVED) {
			// the bundle failed to start, most probably because its
			// start level is too high
			int bundleStartLevel = rBundle.getBundleStartLevel();
			int fwStartLevel = ((FrameworkImpl) getBundle().findFramework()).getFrameWorkStartLevel();
			if (fwStartLevel < bundleStartLevel)
				return Util.newStatus(IStatus.WARNING, Messages.bundle_start_failure, null);
		}
		return Status.OK_STATUS;
	}

	protected String getMessage(IStatus operationStatus) {
		return NLS.bind(Messages.bundle_startup_failure, getBundle().toString());
	}
}