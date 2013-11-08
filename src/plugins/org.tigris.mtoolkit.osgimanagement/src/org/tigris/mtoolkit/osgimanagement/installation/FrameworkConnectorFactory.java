/*******************************************************************************
 * Copyright (c) 2005, 2009 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.osgimanagement.installation;

import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.tigris.mtoolkit.iagent.DeviceConnectionEvent;
import org.tigris.mtoolkit.iagent.DeviceConnectionListener;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.osgimanagement.internal.FrameworkPlugin;
import org.tigris.mtoolkit.osgimanagement.internal.FrameworksView;
import org.tigris.mtoolkit.osgimanagement.internal.Messages;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.FrameworkImpl;
import org.tigris.mtoolkit.osgimanagement.internal.browser.treeviewer.action.ActionsManager;
import org.tigris.mtoolkit.osgimanagement.model.Framework;

public final class FrameworkConnectorFactory {
  public static final int                       CONNECT_PROGRESS            = 1000;
  public static final int                       CONNECT_PROGRESS_CONNECTING = (int) (CONNECT_PROGRESS * 0.1);
  public static final int                       CONNECT_PROGRESS_BUNDLES    = (int) (CONNECT_PROGRESS * 0.3);
  public static final int                       CONNECT_PROGRESS_SERVICES   = (int) (CONNECT_PROGRESS * 0.2);
  public static final int                       CONNECT_PROGRESS_ADDITIONAL = (int) (CONNECT_PROGRESS * 0.4);

  private static final DeviceConnectionListener listener                    = new DeviceConnectionListenerImpl();

  private FrameworkConnectorFactory() {
  }

  public static void init() {
    DeviceConnector.addDeviceConnectionListener(listener);
  }

  public static void deinit() {
    DeviceConnector.removeDeviceConnectionListener(listener);
  }

  public static void connectFramework(final Framework fw) {
    ConnectFrameworkJob job = new ConnectFrameworkJob(fw);
    job.schedule();
  }

  public static IStatus connectFrameworkSync(final Framework fw, IProgressMonitor monitor) {
    ConnectFrameworkJob job = new ConnectFrameworkJob(fw);
    return job.run(monitor);
  }

  public static boolean isConnecting(Framework fw) {
    return ConnectFrameworkJob.isConnecting(fw);
  }

  public static String generateFrameworkName(Dictionary connProps, String identifier) {
    Hashtable frameWorkMap = new Hashtable();
    FrameworkImpl fws[] = FrameworksView.getFrameworks();
    if (fws != null) {
      for (int i = 0; i < fws.length; i++) {
        frameWorkMap.put(fws[i].getName(), ""); //$NON-NLS-1$
      }
    }

    Object ip = connProps.get(DeviceConnector.TRANSPORT_ID);
    String defaultFWName = Messages.new_framework_default_name + " (" + connProps.get(DeviceConnector.TRANSPORT_TYPE)
        + "=" + connProps.get(DeviceConnector.TRANSPORT_ID) + ")";
    if (identifier != null) {
      defaultFWName += " (" + identifier + ")";
    }
    String frameWorkName = defaultFWName;
    String suffix = " ";
    if (ip != null) {
      suffix += ip;
    }
    int index = 1;
    while (frameWorkMap.containsKey(frameWorkName)) {
      frameWorkName = defaultFWName + suffix + "(" + index + ")";
      index++;
    }
    return frameWorkName;
  }

  private static final class DeviceConnectionListenerImpl implements DeviceConnectionListener {
    /* (non-Javadoc)
     * @see org.tigris.mtoolkit.iagent.DeviceConnectionListener#deviceConnectionEvent(org.tigris.mtoolkit.iagent.DeviceConnectionEvent)
     */
    public void deviceConnectionEvent(DeviceConnectionEvent event) {
      if (event.getType() == DeviceConnectionEvent.DISCONNECTED) {
        final DeviceConnector connector = event.getConnector();
        FrameworkImpl fwArr[] = FrameworksView.findFramework(connector);
        if (fwArr == null) {
          return;
        }
        for (int j = 0; j < fwArr.length; j++) {
          FrameworkImpl fw = fwArr[j];
          FrameworkPlugin.debug("FrameworkPlugin: " + fw.getName() + " was disconnected with connector: " + connector); //$NON-NLS-1$ //$NON-NLS-2$
          synchronized (fw.getLockObject()) {
            ActionsManager.disconnectConsole(fw);
            FrameworkImpl fws[] = FrameworksView.getFrameworks();
            if (fws != null) {
              for (int i = 0; i < fws.length; i++) {
                fw = fws[i];
                if (fw.getConnector() != null && fw.getConnector().equals(connector)) {
                  fw.disconnect();
                  fw.setPMPConnectionListener(null);
                  if (fw.isAutoConnected()) {
                    fw.dispose();
                  }
                  break;
                }
              }
            }
          }
        }
      }
    }
  }
}
