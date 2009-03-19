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
package org.tigris.mtoolkit.osgimanagement.internal.browser.model;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.XMLMemento;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteBundle;
import org.tigris.mtoolkit.iagent.RemoteDP;
import org.tigris.mtoolkit.iagent.RemoteService;
import org.tigris.mtoolkit.iagent.event.RemoteBundleEvent;
import org.tigris.mtoolkit.iagent.event.RemoteBundleListener;
import org.tigris.mtoolkit.iagent.event.RemoteDPEvent;
import org.tigris.mtoolkit.iagent.event.RemoteDPListener;
import org.tigris.mtoolkit.iagent.event.RemoteServiceEvent;
import org.tigris.mtoolkit.iagent.event.RemoteServiceListener;
import org.tigris.mtoolkit.osgimanagement.internal.FrameWorkView;
import org.tigris.mtoolkit.osgimanagement.internal.Messages;
import org.tigris.mtoolkit.osgimanagement.internal.browser.logic.BrowserErrorHandler;
import org.tigris.mtoolkit.osgimanagement.internal.browser.logic.FrameworkConnectorFactory;
import org.tigris.mtoolkit.osgimanagement.internal.browser.logic.PMPConnectionListener;


public class FrameWork extends Model implements RemoteBundleListener, RemoteDPListener, RemoteServiceListener {
  
  public final static int BUNDLES_VIEW = 0;
  public final static int SERVICES_VIEW = 1;
    
  private boolean showBundlesID = false;
  private boolean showBundlesVersion = false;
	
  public Hashtable bundleHash;
  public Hashtable categoryHash;
  public Hashtable dpHash;

  // stores services info during connect action
  public Vector servicesVector;
  // stores services view nodes
  public Vector servicesViewVector;

  
  private IMemento configs;
  private int viewType;
  
  private Model bundles;
  private Model deplPackages;
  
  private DeviceConnector connector;
  public boolean autoConnected;
  private Display display;

  public IProgressMonitor monitor;
  private boolean disposing = false;
  private boolean refreshing = false;
  private boolean connecting = false;
  
  private PMPConnectionListener connectionListener;
  private boolean connectedFlag;

  public FrameWork(String name, Model parent, boolean autoConnected) {
    super(name, parent);
    this.autoConnected = autoConnected;
    configs = XMLMemento.createWriteRoot(MEMENTO_TYPE);
    configs.putString(FRAMEWORK_ID, name);
  }
  
  public Model getBundlesNode() {
    if (bundles == null) {
      bundles = new SimpleNode(Messages.bundles_node_label, this);
      addElement(bundles);
    }
    return bundles;
  }
  
  public Model getDPNode() {
    if (deplPackages == null) {
      deplPackages = new SimpleNode(Messages.dpackages_node_label, this);
      addElement(deplPackages);
    }
    return deplPackages;
  }


  public void removeElement(Model element) {
	super.removeElement(element);

	if (element == bundles) {
		bundles = null;
	}
	if (element == deplPackages) {
		deplPackages = null;
	}
  }

  public void setConfig(IMemento configs) {
    this.configs = configs;
  }


  public IMemento getConfig() {
    return configs;
  }

  public void dispose() {
    disposing = true;
    if (this.isConnected()) {
      this.disconnect();
    }
    FrameWorkView.treeRoot.removeElement(this);
  }

  public boolean isConnecting() {
    return connecting;
  }
  
  public boolean isConnected() {
    return connectedFlag;
  }
  
  private void refreshViewers() {
    display = Display.getDefault();
    if (display == null) display = Display.getCurrent();
    display.asyncExec(new Runnable() {
      public void run() {
        TreeViewer[] all = FrameWorkView.getTreeViewers();
        if (all != null) {
          for (int i = 0; i < all.length; i++) {
            if (!all[i].getControl().isDisposed()) {
              all[i].refresh();
            }
          }
        }
      }
    });
  }
  
  public void setConnector(DeviceConnector connector) {
    this.connector = connector;
  }
  
  public void setMonitor(IProgressMonitor monitor) {
    this.monitor = monitor;
    
  }
  
  public void connected(final DeviceConnector connector) {
    this.connectedFlag = true;
    this.connector = connector;
    Job joconnectJob = (Job) FrameworkConnectorFactory.connectJobs.get(connector.getProperties().get("framework-name")); //$NON-NLS-1$
    if (joconnectJob != null) {
      joconnectJob.setName(Messages.retrieve_framework_info);
      try {
        joconnectJob.join();
      } catch (InterruptedException e) {
        BrowserErrorHandler.processError(e, false);
      }
    }
    
    Job job = new Job(Messages.retrieve_framework_info) {
      protected IStatus run(final IProgressMonitor monitor) {
        FrameWork.this.monitor = monitor;
        synchronized (FrameworkConnectorFactory.getLockObject(connector)) {
          connectFramework();
          if (monitor.isCanceled()) {
            FrameworkConnectorFactory.disconnectFramework(FrameWork.this);
            FrameworkConnectorFactory.disconnectConsole(getName());
          }
        }
        return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
      }
    };
    job.schedule();
    try {
      job.join();
    } catch (InterruptedException e) {
    	BrowserErrorHandler.processError(e, false);
    }
    FrameworkConnectorFactory.connectJobs.remove(connector.getProperties().get("framework-name")); //$NON-NLS-1$
  }
  
  private void connectFramework() {
    try {
      connecting = true;
      if (connector != null) {
        bundleHash = new Hashtable();
        categoryHash = new Hashtable();
        servicesVector = new Vector();
        servicesViewVector = new Vector();
        
        dpHash = new Hashtable();
        
        refreshViewers();
        
        try {
          if (monitor != null && monitor.isCanceled()) return;
          connector.getDeploymentManager().addRemoteBundleListener(this);
          connector.getDeploymentManager().addRemoteDPListener(this);
          connector.getServiceManager().addRemoteServiceListener(this);
        } catch (IAgentException e) {
          e.printStackTrace();
          BrowserErrorHandler.processError(e, connector);
        }

        viewType = BUNDLES_VIEW;
        try {
          if (monitor != null && monitor.isCanceled()) return;
          FrameworkConnectorFactory.addBundles(this, true, monitor);
          if (monitor != null && monitor.isCanceled()) return;
          
          try {
            FrameworkConnectorFactory.addDP(this, monitor);
          } catch (IAgentException e) {
            // if deployment admin was not found show only warning
            if (e.getErrorCode() == -5001) {
              BrowserErrorHandler.processWarning(e, null, true); 
            } else {
              BrowserErrorHandler.processError(e, connector);
            }
          }
            
          if (monitor != null && monitor.isCanceled()) return;
          FrameworkConnectorFactory.addServicesNodes(this);

        } catch (IAgentException e) {
            BrowserErrorHandler.processError(e, connector);
        } catch (IllegalStateException ise) {
          // connection was closed
        }
      }
      updateContextMenuStates();
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      connecting = false;
    }
    this.monitor = null;
    if (!refreshing) {
      BrowserErrorHandler.processInfo(connector.getProperties().get("framework-name")+" successfully "+ "connected", false); //$NON-NLS-1$
    }
  }
  
  public DeviceConnector getConnector() {
    return connector;
  }

  public void disconnect() {
    if (!connectedFlag) return;
    if (!refreshing) {
      this.connectedFlag = false;
    }
    
    if (connector != null) {
      if (monitor != null) {
        monitor.setCanceled(true);
      }
      if (!refreshing) {
        connectionListener.disconnected();
      }
      
      try {
        if (connector.isActive()) {
          connector.getDeploymentManager().removeRemoteBundleListener(this);
          connector.getDeploymentManager().removeRemoteDPListener(this);
          connector.getServiceManager().removeRemoteServiceListener(this);
        }
      } catch (IAgentException e) {
        e.printStackTrace();
        BrowserErrorHandler.processError(e, connector);
      }
    }

    if (!disposing) {
      display = Display.getCurrent();
      if (display == null) display = Display.getDefault();

      display.asyncExec(new Runnable() {
        public void run() {
          TreeViewer views[] = FrameWorkView.getTreeViewers();
          if (views != null && views.length > 0) {
            views[0].collapseToLevel(this, TreeViewer.ALL_LEVELS);
          }
          removeChildren();
        }
      });
    }
    deplPackages = null;
    bundles = null;
    
    setViewType(BUNDLES_VIEW);
    
    if (bundleHash != null) bundleHash.clear(); 
    if (categoryHash != null) categoryHash.clear();
    if (servicesVector != null) servicesVector.removeAllElements();
    if (servicesViewVector != null) servicesViewVector.removeAllElements();
    if (dpHash != null) dpHash.clear();
    bundleHash = null;
    categoryHash = null;
    servicesVector = null;
    servicesViewVector = null;
    dpHash = null;
    ServiceObject.usedInHashFWs.remove(this);
    refreshViewers();
    getParent().updateViewers();
    updateContextMenuStates();
    if (!refreshing) {
      BrowserErrorHandler.processInfo(connector.getProperties().get("framework-name")+" successfully "+ "disconnected", false); //$NON-NLS-1$
    }

  }

  public void setDPHash(Hashtable hash) {
    dpHash = hash;
  }

  public void addToBundleHash(long id, Bundle bundle) {
    if (bundleHash == null) return;
    bundleHash.put(new Long(id), bundle);
  }

  public void addToCategoryHash(String name, Category category) {
    if (categoryHash == null) return;
    categoryHash.put(name, category);
  }
  
  public void addToDPHash(String name, DeploymentPackage dp) {
    if (dpHash == null) return;
    dpHash.put(name, dp);
  }

  public void removeFromBundleHash(long id) {
    if (bundleHash == null) return;
    bundleHash.remove(new Long(id));
  }

  public void removeFromCategoryHash(String name) {
    if (categoryHash == null) return;
    categoryHash.remove(name);
  }
  
  public void removeFromDPHash(String name) {
    if (dpHash == null) return;
    dpHash.remove(name);
  }

  public Bundle findBundle(long id) {
    if (bundleHash == null) return null;
    return (Bundle)bundleHash.get(new Long(id)); 
  }
  
  public Bundle findBundleForService(long id) throws IAgentException {
    if (bundleHash == null) return null;
    Object bundles[] = bundleHash.values().toArray();
    for (int i=0; i<bundles.length; i++) {
      Model serviceCategories[] = ((Bundle)bundles[i]).getChildren();
      if (serviceCategories != null && serviceCategories.length > 0) {
        Object services[] = serviceCategories[0].getChildren();
        if (services != null) {
          for (int j=0; j<services.length; j++) {
            if (((ObjectClass)services[j]).getService().getServiceId() == id) {
              return (Bundle)bundles[i];
            }
          }
        }
      }
    }
    return null;
  }
  
  public Bundle findBundleInDP(long id) {
    Enumeration deps = dpHash.elements();
    while (deps.hasMoreElements()) {
      DeploymentPackage dp = (DeploymentPackage) deps.nextElement();
      Model bundles[] = dp.getChildren();
      for (int i=0; i<bundles.length; i++) {
        if (((Bundle)bundles[i]).getID() == id) {
          return ((Bundle)bundles[i]);
        }
      }
    }
    return null;
  }


  public Bundle findBundle(Object id) {
    if (bundleHash == null) return null;
    return (Bundle)bundleHash.get(id);
  }


  public Category findCategory(String name) {
    if (categoryHash == null) return null;
    return (Category)categoryHash.get(name);
  }
  
  public DeploymentPackage findDP(String name) {
    if (dpHash == null) return null;
    return (DeploymentPackage)dpHash.get(name);
  }

  public Vector getCategoriesKeys() {
    if ((categoryHash == null) || (categoryHash.size() == 0)) {
      return null;
    }
    Vector res = new Vector(categoryHash.size());
    Enumeration keys = categoryHash.keys();
    while (keys.hasMoreElements()) {
      res.addElement(keys.nextElement());
    }
    return res;
  }


  public int getBundlesSize() {
    if (bundleHash == null) {
      return 0;
    }
    return bundleHash.size();
  }


  public Vector getBundlesKeys() {
    if ((bundleHash == null) || (bundleHash.size() == 0)) {
      return null;
    }
    Vector res = new Vector(bundleHash.size());
    Enumeration keys = bundleHash.keys();
    while (keys.hasMoreElements()) {
      res.addElement(keys.nextElement());
    }
    return res;
  }

  public int getDPSize() {
    if (dpHash == null) {
      return 0;
    }
    return dpHash.size();
  }
  
  public Vector getDPKeys() {
    if ((dpHash == null) || (dpHash.size() == 0)) {
      return null;
    }
    Vector res = new Vector(dpHash.size());
    Enumeration keys = dpHash.keys();
    while (keys.hasMoreElements()) {
      res.addElement(keys.nextElement());
    }
    return res;
  }

  // Overrides method in Model class
  public boolean testAttribute(Object target, String name, String value) {
    if (!(target instanceof
     org.tigris.mtoolkit.osgimanagement.internal.browser.model.FrameWork)) {
      return false;
    }

    if (name.equalsIgnoreCase(FRAMEWORK_STATUS_NAME)) {
      if (value.equalsIgnoreCase(FRAMEWORK_CONNECT_VALUE)) {
        return this.isConnected();
      }
    }
    return false;
  }

	public int getViewType() {
		return viewType;
	}


	public void setViewType(int i) {
		viewType = i;
	}


  protected boolean isShowBundlesID() {
  	return showBundlesID;
  }
  
  protected void setShowBundlesID(boolean b) {
  	showBundlesID = b;
  }

  protected boolean isShowBundlesVersion() {
    return showBundlesVersion;
  }
  
  protected void setShowBundlesVersion(boolean b) {
    showBundlesVersion = b;
  }

  
  // find and update nodes for bundle in "Bundles" and "Deployment packages"
  private void updateBundleNodes(RemoteBundle rBundle) throws IAgentException {
    long id = rBundle.getBundleId();
    Bundle node = findBundle(id);
    if (node != null) {
      node.update();
      node = findBundleInDP(id);
      if (node != null) {
        node.update();
        node.updateViewers();
      }
    }
    
    for (int i=0; servicesViewVector != null && i<servicesViewVector.size(); i++) {
      Model service = (Model) servicesViewVector.elementAt(i);
      Model children[] = service.getChildren();
      for (int j=0; j<children.length; j++) {
        Model bundles[] = children[j].getChildren();
        for (int k=0; k<bundles.length; k++) {
          Bundle bundle = (Bundle) bundles[k];
          if (bundle.getID() == id) {
            bundle.update();
            bundle.updateViewers();
          }
        }
      }
    }
  }
  
  private void udpateBundleCategory(RemoteBundle rBundle) throws IAgentException {
    long id = rBundle.getBundleId();
    
    // remove bundle and empty category
    Bundle bundle = findBundle(id);
    bundleHash.remove(new Long(id));
    if (bundle != null) {
      Category category = (Category) bundle.getParent();
      category.removeElement(bundle);
      if (category.getSize() == 0) {
        categoryHash.remove(category.getName());
        category.getParent().removeElement(category);
      }
    }
    removeBundleInServicesView(id);
    FrameworkConnectorFactory.addBundle(rBundle, this);
  }
  
  private void removeBundle(long id) throws IAgentException {
    // remove bundle and empty category
    Bundle bundle = findBundle(id);
    bundleHash.remove(new Long(id));
    if (bundle != null) {
      Category category = (Category) bundle.getParent();
      category.removeElement(bundle);
      if (category.getSize() == 0) {
        categoryHash.remove(category.getName());
        category.getParent().removeElement(category);
      }

      // remove bundle from DP
      bundle = findBundleInDP(bundle.getID());
      if (bundle != null) {
        bundle.getParent().removeElement(bundle);
      }
    }

    removeBundleInServicesView(id);
  }
  
  private void removeBundleInServicesView(long id) {
    // remove bundles from services view nodes
    for (int i=servicesViewVector.size()-1; i>=0; i--) {
      ObjectClass objClass = (ObjectClass) servicesViewVector.elementAt(i);
      Model children[] = objClass.getChildren();
      for (int j=0; j<children.length; j++) {
        Model bundles[] = children[j].getChildren();
        for (int k=0; k<bundles.length; k++) {
          if (((Bundle)bundles[k]).getID() == id) {
            children[j].removeElement(bundles[k]);
            if (j == 0)
              servicesViewVector.removeElementAt(i);
            break;
          }
        }
      }
    }
  }
  
  private String getDebugBundleChangedMsg(RemoteBundleEvent e) {
    int type = e.getType();
    String debug = "Bundle state changed "+type; //$NON-NLS-1$
    
    try {
      if (type == RemoteBundleEvent.UNINSTALLED) {
        debug = "Bundle "+e.getBundle().getBundleId() + " uninstalled"; //$NON-NLS-1$ //$NON-NLS-2$
      } else {
        debug = "Bundle "+FrameworkConnectorFactory.getBundleName(e.getBundle(), null)+" "; //$NON-NLS-1$ //$NON-NLS-2$
        switch (type) {
          case (RemoteBundleEvent.INSTALLED): {
            debug += "INSTALLED"; //$NON-NLS-1$
            break;
          }
          case (RemoteBundleEvent.LAZY_STARTED): {
            debug += "LAZY_STARTED"; //$NON-NLS-1$
            break;
          }
          case (RemoteBundleEvent.RESOLVED): {
            debug += "RESOLVED"; //$NON-NLS-1$
            break;
          }
          case (RemoteBundleEvent.STARTED): {
            debug += "STARTED"; //$NON-NLS-1$
            break;
          }
          case (RemoteBundleEvent.STOPPED): {
            debug += "STOPPED"; //$NON-NLS-1$
            break;
          }
          case (RemoteBundleEvent.UNRESOLVED): {
            debug += "UNRESOLVED"; //$NON-NLS-1$
            break;
          }
          case (RemoteBundleEvent.UPDATED): {
            debug += "UPDATED"; //$NON-NLS-1$
            break;
          }
          case (RemoteBundleEvent.STARTING): {
            debug += "STARTING"; //$NON-NLS-1$
            break;
          }
          case (RemoteBundleEvent.STOPPING): {
            debug += "STOPPING"; //$NON-NLS-1$
            break;
          }
        }
      }
    } catch (IAgentException e1) {
    }
    return debug;
  }

  public void bundleChanged(RemoteBundleEvent e) {
    BrowserErrorHandler.debug(getDebugBundleChangedMsg(e));
    
    if (!isConnected()) return;
    try {
      final RemoteBundle rBundle = e.getBundle();
      long id = rBundle.getBundleId();
      final int type = e.getType();
      try {
        if (!bundleHash.containsKey(new Long(id)) && type != RemoteBundleEvent.UNINSTALLED) {
          FrameworkConnectorFactory.addBundle(rBundle, FrameWork.this);
        } else if (type == RemoteBundleEvent.UPDATED) {
          Bundle bundle = findBundle(id);
          String category = rBundle.getHeader("Bundle-Category", ""); //$NON-NLS-1$ //$NON-NLS-2$
          if (!bundle.getParent().getName().equals(category)) {
            udpateBundleCategory(rBundle);
          }
          bundle = findBundle(id);
          String newName = FrameworkConnectorFactory.getBundleName(rBundle, null);
          bundle.name = newName;
          bundle = findBundleInDP(id);
          if (bundle != null) {
            bundle.name = newName;
          }
          updateBundleNodes(rBundle);
        } else if (type == RemoteBundleEvent.UNINSTALLED) {
          removeBundle(rBundle.getBundleId());
        } else if (type == RemoteBundleEvent.STOPPED) {
          Bundle bundle = findBundle(rBundle.getBundleId());
          bundle.removeChildren();
          bundle = findBundleInDP(bundle.getID());
          if (bundle != null) {
            bundle.removeChildren();
          }
          removeBundleInServicesView(rBundle.getBundleId());
          updateBundleNodes(rBundle);
        } else if (type == RemoteBundleEvent.STARTED) {
          Bundle bundle = findBundle(rBundle.getBundleId());
          Bundle dpBundle = findBundleInDP(rBundle.getBundleId());
          bundle.setState(org.osgi.framework.Bundle.ACTIVE);
          if (dpBundle != null) dpBundle.setState(org.osgi.framework.Bundle.ACTIVE);

          RemoteService usedServ[] = bundle.getRemoteBundle().getServicesInUse();
          FrameworkConnectorFactory.addServiceCategoriesNodes(bundle);
          if (dpBundle != null) FrameworkConnectorFactory.addServiceCategoriesNodes(dpBundle);

          Model bundleCategory = bundle.getChildren()[1];
          Model dpBundleCategory = dpBundle == null ? null : dpBundle.getChildren()[1];
          
          for (int i=0; i<usedServ.length; i++) {
            FrameworkConnectorFactory.createObjectClassNodes(bundleCategory, usedServ[i].getObjectClass(), new Long(usedServ[i].getServiceId()), usedServ[i]);
            if (dpBundleCategory != null) FrameworkConnectorFactory.createObjectClassNodes(dpBundleCategory, usedServ[i].getObjectClass(), new Long(usedServ[i].getServiceId()), usedServ[i]);              
            
            for (int j=0; j<servicesViewVector.size(); j++) {
              ObjectClass oc = (ObjectClass) servicesViewVector.elementAt(j);
              if (oc.getNameID().longValue() == usedServ[i].getServiceId()) {
                BundlesCategory bCategory = (BundlesCategory) oc.getChildren()[1];
                Model bundles[] = bCategory.getChildren();
                boolean added = false;
                for (int k=0; k<bundles.length; k++) {
                  if (((Bundle)bundles[k]).getID() == bundle.getID()) {
                    added = true;
                    break;
                  }
                }
                if (!added) {
                  Bundle usedInBundle = new Bundle(bundle.getName(), bCategory, bundle.getRemoteBundle(), bundle.getState(), bundle.getType(), bundle.getCategory());
                  bCategory.addElement(usedInBundle);
                }
              }
            }

          }

          updateBundleNodes(rBundle);
        } else {
          updateBundleNodes(rBundle);
        }
      } catch (IAgentException ex) {
        ex.printStackTrace();
        BrowserErrorHandler.processError(ex, connector);
      } catch (IllegalStateException ex) {
    	  BrowserErrorHandler.debug(ex);
      } catch (Throwable t) {
        t.printStackTrace();
        BrowserErrorHandler.processError(t, connector);
      }
      updateContextMenuStates();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public void deploymentPackageChanged(final RemoteDPEvent e) {
    try {  BrowserErrorHandler.debug("Deployment package changed "+e.getDeploymentPackage().getName()+" "+(e.getType() == RemoteDPEvent.INSTALLED ? "INSTALLED" : "UNINSTALLED"));     } catch (IAgentException e2) {} //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    
    if (!isConnected()) return;
    try {
      RemoteDP remoteDP = e.getDeploymentPackage();
      if (e.getType() == RemoteDPEvent.INSTALLED) {
        Model dpNodeRoot = getDPNode();
        try {
          //check if this install actually is update
          DeploymentPackage dp = findDP(remoteDP.getName());
          if (dp != null) {
            getDPNode().removeElement(dp);
          }
          
          DeploymentPackage dpNode = new DeploymentPackage(remoteDP, getDPNode(), FrameWork.this);
          dpNodeRoot.addElement(dpNode);
          dpHash.put(remoteDP.getName(), dpNode);
        } catch (IAgentException e1) {
          e1.printStackTrace();
          BrowserErrorHandler.processError(e1, connector);
        }
      } else if (e.getType() == RemoteDPEvent.UNINSTALLED) {
        try {
          if (remoteDP != null) {
            DeploymentPackage dpNode = (DeploymentPackage) dpHash.remove(remoteDP.getName());
      	  // there are cases where the dp failed to be added, because it was too quickly uninstalled/updated
      	  if (dpNode != null)	
      		  getDPNode().removeElement(dpNode);
          }
        } catch (IAgentException e1) {
          e1.printStackTrace();
          BrowserErrorHandler.processError(e1, connector);
        }
      }

      updateViewers();
    } catch (IllegalStateException ex) {
    	// ignore state exceptions, which usually indicates that something is was fast enough to disappear
    	BrowserErrorHandler.debug(ex);
    } catch (Throwable t) {
      t.printStackTrace();
      BrowserErrorHandler.processError(t, connector);
    }
    updateContextMenuStates();
  }
  
  public void removeService(long id) throws IAgentException {
    try {
      Enumeration bundlesKeys = bundleHash.keys();
      while (bundlesKeys.hasMoreElements()) {
        Object key = bundlesKeys.nextElement();
        Bundle bundle = (Bundle) bundleHash.get(key);
        Model children[] = bundle.getChildren();
        for (int i=0; i<children.length; i++) {
          Model services[] = children[i].getChildren();
          for (int j=0; j<services.length; j++) {
            if (((ObjectClass)services[j]).getNameID().longValue() == id) {
              children[i].removeElement(services[j]);
            }
          }
        }
      }

      Enumeration dpKeys = dpHash.keys();
      while (dpKeys.hasMoreElements()) {
        Object key = dpKeys.nextElement();
        DeploymentPackage dp = (DeploymentPackage) dpHash.get(key);
        Model bundles[] = dp.getChildren();
        for (int i=0; i<bundles.length; i++) {
          Bundle bundle = (Bundle) bundles[i];

          Model children[] = bundle.getChildren();
          for (int j=0; j<children.length; j++) {
            Model services[] = children[j].getChildren();
            for (int k=0; k<services.length; k++) {
              if (((ObjectClass)services[k]).getNameID().longValue() == id) {
                children[j].removeElement(services[k]);
              }
            }
          }
        }
      }

      for (int i=servicesViewVector.size()-1; i>=0; i--) {
        ObjectClass objClass = (ObjectClass) servicesViewVector.elementAt(i);
        if (objClass.getService().getServiceId() == id) {
          if (viewType == SERVICES_VIEW) {
            removeElement(objClass);
          }
          servicesViewVector.removeElementAt(i);
        }
      }
    } catch (Throwable t) {
      BrowserErrorHandler.processError(t, true);
    }
  }
  
  private String getDebugServiceChangedMsg(RemoteServiceEvent e) {
    String type = ""; //$NON-NLS-1$
    try {
      switch (e.getType()) {
        case RemoteServiceEvent.UNREGISTERED: {
          type = "UNREGISTERED"; //$NON-NLS-1$
          break;
        }
        case RemoteServiceEvent.MODIFIED: {
          type = "MODIFIED"; //$NON-NLS-1$
          break;
        }
        case RemoteServiceEvent.REGISTERED: {
          type = "REGISTERED"; //$NON-NLS-1$
          break;
        }
        
      }
      return "Service id = "+e.getService().getServiceId()+" "+type; //$NON-NLS-1$ //$NON-NLS-2$
    } catch (IAgentException e2) {
    }
    return "Service " + type + " " + e;  //$NON-NLS-1$ //$NON-NLS-2$
  }
  
  public void serviceChanged(final RemoteServiceEvent e) {
    BrowserErrorHandler.debug(getDebugServiceChangedMsg(e));
    if (!isConnected()) return;
    try {
      RemoteService rService = e.getService();
      if (e.getType() == RemoteServiceEvent.UNREGISTERED) {
        removeService(rService.getServiceId());
      } else {
        RemoteBundle rBundle = rService.getBundle();
        if (rBundle != null) {
          Bundle bundle = findBundle(rBundle.getBundleId());
          if (bundle == null) {
            FrameworkConnectorFactory.addBundle(rBundle, this);
          }
          if (e.getType() == RemoteServiceEvent.REGISTERED) {
            ServiceObject servObj = new ServiceObject(rService, rBundle);
            RemoteBundle usedInBundles[] = rService.getUsingBundles();
            for (int i=0; i<usedInBundles.length; i++) {
              ServiceObject.addUsedInBundle(rService, usedInBundles[i], this);
            }
            FrameworkConnectorFactory.addServiceNodes(this, servObj);
          }
        }
      }
      updateViewers();
    } catch (IAgentException e1) {
      BrowserErrorHandler.processError(e1, connector);
    } catch (IllegalStateException ex) {
    	// ignore illegal states, they are usually due to working with stale data
    	BrowserErrorHandler.debug(ex);
    } catch (Throwable t) {
      BrowserErrorHandler.processError(t, connector);
    }
  }
  
  private void updateContextMenuStates() {
    display = Display.getCurrent();
    if (display == null) display = Display.getDefault();
    display.asyncExec(new Runnable() {
      public void run() {
        FrameWorkView.updateContextMenuStates();
      }
    });
  }
  
  public void refreshAction() {
    refreshing = true;
    DeviceConnector conn2 = connector;
    disconnect();
    connector = conn2;
    display.asyncExec(new Runnable() { 
      public void run() {
        Job job = new Job(Messages.refresh_framework_info) {
          protected IStatus run(final IProgressMonitor monitor) {
            FrameWork.this.monitor = monitor;
            connectFramework();
            refreshing = false;
            return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
          }
        };
        job.schedule();
      }
    });
  }

  public void setPMPConnectionListener(PMPConnectionListener connectionListener) {
    this.connectionListener = connectionListener;
  }
  
  public PMPConnectionListener getPMPConnectionListener() {
    return connectionListener;
  }
  
  public void refreshBundleAction(Bundle sourceBundle) {
    try {
      long id = sourceBundle.getID();
      RemoteService regServ[] = sourceBundle.getRemoteBundle().getRegisteredServices();
      RemoteService usedServ[] = sourceBundle.getRemoteBundle().getServicesInUse();
      
      Bundle bundle = findBundleInDP(id);
      if (bundle != null) {
        updateBundleServices(bundle, regServ, usedServ);
      }
      
      bundle = findBundle(id);
      updateBundleServices(bundle, regServ, usedServ);

      
      // remove bundle in services view
      for (int i=servicesViewVector.size()-1; i>=0; i--) {
        Model service = (Model) servicesViewVector.elementAt(i);
        Model children[] = service.getChildren();
        for (int j=0; j<children.length; j++) {
          Model bundles[] = children[j].getChildren();
          for (int k=0; k<bundles.length; k++) {
            bundle = (Bundle) bundles[k];
            if (bundle.getID() == id) {
              children[j].removeElement(bundle);
              // remove bundles registered services
              if (j == 0) {
                servicesViewVector.removeElementAt(i);
                removeElement(service);
              }
            }
          }
        }
      }
      
      for (int i=0; i<regServ.length; i++) {
        String objClass[] = regServ[i].getObjectClass();
        for (int j=0; j<objClass.length; j++) {
          ObjectClass oc = new ObjectClass(this, objClass[j]+" [Service "+regServ[i].getServiceId()+"]", new Long(regServ[i].getServiceId()), regServ[i]);
          BundlesCategory regCategory = new BundlesCategory(oc, BundlesCategory.REGISTERED);
          BundlesCategory usedCategory = new BundlesCategory(oc, BundlesCategory.IN_USE);
          oc.addElement(regCategory);
          oc.addElement(usedCategory);
          Bundle newBundle = new Bundle(sourceBundle.getName(), regCategory, sourceBundle.getRemoteBundle(), sourceBundle.getState(), sourceBundle.getType(), sourceBundle.getCategory());
          regCategory.addElement(newBundle);
          servicesViewVector.addElement(oc);
          if (viewType == SERVICES_VIEW) {
            addElement(oc);
            oc.updateViewers();
          }
        }
      }
      
      for (int i=0; i<usedServ.length; i++) {
        long usedInId = usedServ[i].getServiceId();
        for (int j=0; j<servicesViewVector.size(); j++) {
          ObjectClass oc = (ObjectClass) servicesViewVector.elementAt(j);
          if (oc.getService().getServiceId() == usedInId) {
            oc.getChildren()[1].addElement(new Bundle(sourceBundle.getName(), oc.getChildren()[1], sourceBundle.getRemoteBundle(), sourceBundle.getState(), sourceBundle.getType(), sourceBundle.getCategory()));
            break;
          }
        }
      }
      
    } catch (IAgentException e) {
      BrowserErrorHandler.processError(e, true);
    } catch (IllegalStateException e) {
      BrowserErrorHandler.processError(e, true);
    }
    
  }
  
  private void updateBundleServices(Bundle bundle, RemoteService regServ[], RemoteService usedServ[]) throws IAgentException {
    bundle.removeChildren();
    FrameworkConnectorFactory.addServiceCategoriesNodes(bundle);
    Model children[] = bundle.getChildren();
    if (children.length == 0) return;
    Model regServCategory = children[0];
    Model usedServCategory = children[1];
    
    for (int i=0; i<regServ.length; i++) {
      String objClass[] = regServ[i].getObjectClass();
      for (int j=0; j<objClass.length; j++) {
        regServCategory.addElement(new ObjectClass(regServCategory, objClass[j]+" [Service "+regServ[i].getServiceId()+"]", new Long(regServ[i].getServiceId()), regServ[i]));
      }
    }
    for (int i=0; i<usedServ.length; i++) {
      String objClass[] = usedServ[i].getObjectClass();
      for (int j=0; j<objClass.length; j++) {
        usedServCategory.addElement(new ObjectClass(usedServCategory, objClass[j]+" [Service "+usedServ[i].getServiceId()+"]", new Long(usedServ[i].getServiceId()), usedServ[i]));
      }
    }

  }
}