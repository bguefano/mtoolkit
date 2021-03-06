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
package org.tigris.mtoolkit.osgimanagement.dp;

import java.util.Iterator;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.graphics.Image;
import org.tigris.mtoolkit.osgimanagement.ContentTypeActionsProvider;
import org.tigris.mtoolkit.osgimanagement.ToolbarIMenuCreator;
import org.tigris.mtoolkit.osgimanagement.dp.actions.DPPropertiesAction;
import org.tigris.mtoolkit.osgimanagement.dp.actions.InstallDPAction;
import org.tigris.mtoolkit.osgimanagement.dp.actions.UninstallDPAction;
import org.tigris.mtoolkit.osgimanagement.dp.images.ImageHolder;
import org.tigris.mtoolkit.osgimanagement.dp.model.DeploymentPackage;
import org.tigris.mtoolkit.osgimanagement.model.Framework;
import org.tigris.mtoolkit.osgimanagement.model.Model;
import org.tigris.mtoolkit.osgimanagement.model.SimpleNode;

public class DPActionsProvider implements ContentTypeActionsProvider {

	private static final String DP_GROUP_IMAGE_PATH = "dp_group.gif";
	private static final String DP_PROPERTIES_IMAGE_PATH = "properties.gif";
	private static final String INSTALL_DP_IMAGE_PATH = "install_dp.gif";
	private static final String UNINSTALL_DP_IMAGE_PATH = "uninstall_dp.gif";
	private static final String DP_ICON_PATH = "dpackage.gif";
	private static final String DP_PACKAGE_PATH = "dp_package.gif";
	
	private InstallDPAction installDPAction;
	private UninstallDPAction uninstallDPAction;
	private DPPropertiesAction dpPropertiesAction;

	private TreeViewer tree;

	public void init(TreeViewer tree) {
		this.tree = tree;
		installDPAction = new InstallDPAction(tree, "Install Deployment Package...");
		installDPAction.setImageDescriptor(ImageHolder.getImageDescriptor(INSTALL_DP_IMAGE_PATH));
		uninstallDPAction = new UninstallDPAction(tree, "Uninstall Deployment Package");
		uninstallDPAction.setImageDescriptor(ImageHolder.getImageDescriptor(UNINSTALL_DP_IMAGE_PATH));
		dpPropertiesAction = new DPPropertiesAction(tree, "Properties");
		dpPropertiesAction.setImageDescriptor(ImageHolder.getImageDescriptor(DP_PROPERTIES_IMAGE_PATH));
	}

	public void fillToolBar(ToolBarManager tbm) {
		Action[] actions = new Action[] { installDPAction, uninstallDPAction, dpPropertiesAction};
		ToolbarIMenuCreator applicationTB = new ToolbarIMenuCreator(actions, tree);
		applicationTB.setImageDescriptor(ImageHolder.getImageDescriptor(DP_GROUP_IMAGE_PATH));
		applicationTB.setToolTipText("Various deployment package actions");
		tbm.appendToGroup(ContentTypeActionsProvider.GROUP_DEPLOYMENT, applicationTB);
	}

	public void menuAboutToShow(StructuredSelection selection, IMenuManager manager) {
		boolean homogen = true;
		if (selection.size() > 0) {
			Model element = (Model) selection.getFirstElement();
			Class clazz = element.getClass();

			Iterator iterator = selection.iterator();
			while (iterator.hasNext()) {
				Object sel = iterator.next();
				if (!clazz.equals(sel.getClass())) {
					homogen = false;
					break;
				}
			}

			if (homogen) {
				if (element instanceof DeploymentPackage) {
					manager.appendToGroup(ContentTypeActionsProvider.GROUP_ACTIONS, installDPAction);
					manager.appendToGroup(ContentTypeActionsProvider.GROUP_ACTIONS, uninstallDPAction);
					manager.appendToGroup(ContentTypeActionsProvider.GROUP_PROPERTIES, dpPropertiesAction);
				} else if (element instanceof SimpleNode && "Deployment Packages".equals(element.getName()) ||
								element instanceof Framework) {
					manager.appendToGroup(ContentTypeActionsProvider.GROUP_INSTALL, installDPAction);
				}
			}
		}
		
	}

	public Image getImage(Model node) {
		if (node instanceof DeploymentPackage) {
			return ImageHolder.getImage(DP_ICON_PATH);
		} else if (node instanceof SimpleNode && 
						"Deployment Packages".equals(node.getName())) {
			return ImageHolder.getImage(DP_PACKAGE_PATH);
		}
		return null;
	}


}
