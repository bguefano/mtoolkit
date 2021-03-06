package org.tigris.mtoolkit.iagent.internal.rpc;

import java.io.IOException;
import java.io.InputStream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.tigris.mtoolkit.iagent.Error;
import org.tigris.mtoolkit.iagent.IAgentErrors;
import org.tigris.mtoolkit.iagent.rpc.spi.BundleManagerDelegate;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;

public class DefaultBundleManagerDelegate implements BundleManagerDelegate {

	private BundleContext bc;
	
	public DefaultBundleManagerDelegate(BundleContext bc) {
		this.bc = bc;
	}
	
	public Object installBundle(String location, InputStream in) {
		try {
			Bundle bundle = bc.installBundle(location, in);
			return bundle;
		} catch (BundleException e) {
			Error error = new Error(IAgentErrors.ERROR_BUNDLE_UNKNOWN, "Failed to install bundle: " + e.getMessage());
			return error;
		}
	}

	public boolean isSupported() {
		return true;
	}

	public Object uninstallBundle(Bundle bundle) {
		try {
			bundle.uninstall();
			return null;
		} catch (BundleException e) {
			return new Error(IAgentErrors.ERROR_BUNDLE_UNKNOWN, "Failed to uninstall bundle: " + DebugUtils.toString(e));
		} catch (IllegalStateException e) {
			return null;	// everything is OK
		}
	}

	public Object updateBundle(Bundle bundle, InputStream in) {
		try {
			bundle.update(in);
			return null;
		} catch (BundleException e) {
			return new Error(IAgentErrors.ERROR_BUNDLE_UNKNOWN, "Failed to update bundle: " + DebugUtils.toString(e));
		} catch (IllegalStateException e) {
			return new Error(Error.BUNDLE_UNINSTALLED_CODE, "Bundle " + bundle.getBundleId() + " has been uninstalled");
		} finally {
			try {
				in.close();
			} catch (IOException e) {
			}
		}
	}

}
