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
package org.tigris.mtoolkit.iagent.tests;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.Bundle;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteBundle;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyEvent;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyListener;

public class RemoteCapabilitiesTest extends DeploymentTestCase implements RemoteDevicePropertyListener {
  private static final String TEST_CAP_BUNDLE                      = "test.bundle.capabilities.setter_1.0.0.jar";
  private static final String CAPABILITY_1                         = "test.bundle.capabilities.setter.cap1";
  private static final String CAPABILITY_2                         = "test.bundle.capabilities.setter.cap2";
  private static final String CAPABILITY_REMAIN                    = "test.bundle.capabilities.setter.cap.remain";

  private RemoteBundle        bundle;
  private final Hashtable     properties                           = new Hashtable();

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.tests.DeploymentTestCase#setUp()
   */
  protected void setUp() throws Exception {
    super.setUp();
    bundle = installBundle(TEST_CAP_BUNDLE);
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.tests.DeploymentTestCase#tearDown()
   */
  protected void tearDown() throws Exception {
    try {
      if (bundle != null && bundle.getState() != Bundle.UNINSTALLED) {
        bundle.uninstall(null);
      }
    } catch (Exception e) {
    }
    super.tearDown();
  }

  public void testGetCapabilities() throws IAgentException {
    try {
      Dictionary props = connector.getProperties();
      Object cap1Value = props.get(CAPABILITY_1);
      if (cap1Value != null) {
        assertEquals("Capability 1 should not be available.", new Boolean(false), cap1Value);
      }
      Object cap2Value = props.get(CAPABILITY_2);
      if (cap2Value != null) {
        assertEquals("Capability 2 should not be available.", new Boolean(false), cap2Value);
      }

      bundle.start(0);

      props = connector.getRemoteProperties();
      cap1Value = props.get(CAPABILITY_1);
      assertEquals("Capability 1 should be available.", new Boolean(true), cap1Value);
      cap2Value = props.get(CAPABILITY_2);
      assertEquals("Capability 2 should be available.", new Boolean(true), cap2Value);

      bundle.stop(0);

      props = connector.getProperties();
      cap1Value = props.get(CAPABILITY_1);
      if (cap1Value != null) {
        assertEquals("Capability 1 should not be available.", new Boolean(false), cap1Value);
      }
      cap2Value = props.get(CAPABILITY_2);
      if (cap2Value != null) {
        assertEquals("Capability 2 should not be available.", new Boolean(false), cap2Value);
      }
    } finally {
      try {
        if (bundle != null && bundle.getState() != Bundle.RESOLVED) {
          bundle.stop(0);
        }
      } catch (Exception e) {
      }
    }
  }

  public void testCapEvents() throws Exception {
    try {
      properties.clear();
      connector.addRemoteDevicePropertyListener(this);

      Thread.sleep(REMOTE_LISTENER_CHANGE_TIMEOUT);

      bundle.start(0);

      // Events are processed asynchronously, wait prop change events to be delivered
      synchronized (properties) {
        try {
          properties.wait(SLEEP_INTERVAL);
        } catch (InterruptedException e) {
        }
      }

      Object cap1Value = properties.get(CAPABILITY_1);
      assertEquals("Capability 1 should be set.", new Boolean(true), cap1Value);
      Object cap2Value = properties.get(CAPABILITY_2);
      assertEquals("Capability 2 should be set.", new Boolean(true), cap2Value);

      bundle.stop(0);
      Thread.sleep(3000);

      cap1Value = properties.get(CAPABILITY_1);
      if (cap1Value != null) {
        assertEquals("Capability 1 should not be available.", new Boolean(false), cap1Value);
      }
      cap2Value = properties.get(CAPABILITY_2);
      if (cap2Value != null) {
        assertEquals("Capability 2 should not be available.", new Boolean(false), cap2Value);
      }

      // Testing properly removing of listeners
      connector.removeRemoteDevicePropertyListener(this);

      Thread.sleep(REMOTE_LISTENER_CHANGE_TIMEOUT);

      properties.clear();
      bundle.start(0);
      Thread.sleep(3000);

      cap1Value = properties.get(CAPABILITY_1);
      if (cap1Value != null) {
        assertEquals("Capability 1 should not be available.", new Boolean(false), cap1Value);
      }
      cap2Value = properties.get(CAPABILITY_2);
      if (cap2Value != null) {
        assertEquals("Capability 2 should not be available.", new Boolean(false), cap2Value);
      }
    } finally {
      try {
        if (bundle != null && bundle.getState() != Bundle.RESOLVED) {
          bundle.stop(0);
        }
        connector.removeRemoteDevicePropertyListener(this);
      } catch (Exception e) {
      }
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyListener#devicePropertiesChanged(org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyEvent)
   */
  public void devicePropertiesChanged(RemoteDevicePropertyEvent e) throws IAgentException {
    synchronized (properties) {
      properties.put(e.getProperty(), e.getValue());
      if (properties.contains(CAPABILITY_1) && properties.contains(CAPABILITY_2)) {
        properties.notifyAll();
      }
    }
  }

  public void testGetCapabilitiesReconnect() throws Exception {
    try {
      bundle.start(0);

      Dictionary props = connector.getRemoteProperties();
      Object capRemainValue = props.get(CAPABILITY_REMAIN);
      assertEquals("Capability 3 should be available.", new Boolean(true), capRemainValue);

      // Disconnect and connect again
      tearDown();
      setUp();

      props = connector.getRemoteProperties();
      capRemainValue = props.get(CAPABILITY_REMAIN);
      assertEquals("Capability 3 should be available.", new Boolean(true), capRemainValue);
    } finally {
      try {
        if (bundle != null && bundle.getState() != Bundle.RESOLVED) {
          bundle.stop(0);
        }
      } catch (Exception e) {
      }
    }
  }
}
