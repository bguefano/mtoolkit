/*******************************************************************************
 * Copyright (c) 2005, 2010 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.certmanager.internal.images;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.tigris.mtoolkit.certmanager.internal.CertManagerPlugin;

public class ImageHolder {

	private static final String IMAGES_PATH = "/images/";

	public static Image getImage(String key) {
		ImageRegistry imgRegistry = CertManagerPlugin.getDefault().getImageRegistry();
		Image image = imgRegistry.get(key);
		if (image == null)
			createImage(key, imgRegistry);
		return imgRegistry.get(key);
	}

	public static ImageDescriptor getImageDescriptor(String key) {
		ImageRegistry imgRegistry = CertManagerPlugin.getDefault().getImageRegistry();
		ImageDescriptor descriptor = imgRegistry.getDescriptor(key);
		if (descriptor == null) {
			descriptor = createImage(key, imgRegistry);
		}
		return descriptor;
	}

	private static ImageDescriptor createImage(String key, ImageRegistry imgRegistry) {
		ImageDescriptor descriptor = CertManagerPlugin.imageDescriptorFromPlugin(CertManagerPlugin.PLUGIN_ID, key);
		if (descriptor == null)
			descriptor = CertManagerPlugin.imageDescriptorFromPlugin(CertManagerPlugin.PLUGIN_ID, IMAGES_PATH + key);
		imgRegistry.put(key, descriptor);
		return descriptor;
	}
}
