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
package org.tigris.mtoolkit.common.installation;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.resource.ImageDescriptor;

/**
 * Installation item processors should implement this interface. One
 * installation item processor can install items of its supported MIME types to
 * specified targets.
 */
public interface InstallationItemProcessor {
	/**
	 * Returns human-readable name of the general target for which this
	 * processor installs items e.g. "OSGi Framework".
	 * 
	 * @return general target name
	 */
	public String getGeneralTargetName();

	/**
	 * Returns image descriptor of the general target for which this
	 * processor installs items e.g. OSGi Framework connected image descriptor.
	 * 
	 * @return general target name
	 */
	public ImageDescriptor getGeneralTargetImageDescriptor();

	/**
	 * Returns array of supported MIME types which this processor can install.
	 * 
	 * @return array of supported MIME types
	 */
	public String[] getSupportedMimeTypes();

	/**
	 * Returns array of all targets to which this item processor can install
	 * items or empty array if there are no targets.
	 * 
	 * @return the array of installation targets
	 */
	public InstallationTarget[] getInstallationTargets();

	/**
	 * Installs passed item to specified target.
	 * <p>
	 * The Install to menu driver must check the supported MIME types of this
	 * processor and make sure that it doesn't pass for processing an
	 * {@link InstallationItem} with unsupported MIME type.
	 * </p>
	 * 
	 * @param item
	 *            the item to install
	 * @param target
	 *            the target where to install the passed item
	 * @param monitor
	 *            the progress monitor to display current progress
	 * 
	 * @return the status of installation
	 */
	public IStatus processInstallationItem(InstallationItem item, InstallationTarget target, IProgressMonitor monitor);
	
}
