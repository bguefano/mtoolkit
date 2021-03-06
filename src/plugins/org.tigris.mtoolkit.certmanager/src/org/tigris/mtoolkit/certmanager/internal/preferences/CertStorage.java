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
package org.tigris.mtoolkit.certmanager.internal.preferences;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.tigris.mtoolkit.certmanager.internal.CertManagerPlugin;
import org.tigris.mtoolkit.common.certificates.ICertificateDescriptor;

public class CertStorage implements ICertificateModifyListener {
	private static final String ATTR_CERT_COUNT = "cert_count"; //$NON-NLS-1$
	private static final String ATTR_CERT_LAST_UID = "cert_last_uid"; //$NON-NLS-1$
	private static final String ATTR_CERT_ALIAS = "cert_alias"; //$NON-NLS-1$
	private static final String ATTR_CERT_LOCATION = "cert_location"; //$NON-NLS-1$
	private static final String ATTR_CERT_TYPE = "cert_type"; //$NON-NLS-1$
	private static final String ATTR_CERT_PASS = "cert_pass"; //$NON-NLS-1$
	private static final String ATTR_CERT_UID = "cert_uid"; //$NON-NLS-1$
	private static final String SECURE_STORAGE_NODE = "org.tigris.mtoolkit.certmanager"; //$NON-NLS-1$

	private List certificates = new ArrayList();
	private List listeners;
	private long lastUid = 0;

	private static CertStorage defaultInstance;

	private CertStorage() {
		load();
	}

	public static CertStorage getDefault() {
		if (defaultInstance == null) {
			defaultInstance = new CertStorage();
		}
		return defaultInstance;
	}

	public void dispose() {
		defaultInstance = null;
	}

	public String generateCertificateUid() {
		return Long.toString(++lastUid);
	}

	public void addCertificate(ICertificateDescriptor cert) {
		certificates.add(cert);
		if (cert instanceof CertDescriptor) {
			((CertDescriptor) cert).addModifyListener(this);
		}
		fireCertificateAdded(cert);
	}

	public void removeCertificate(ICertificateDescriptor cert) {
		if (certificates.remove(cert)) {
			if (cert instanceof CertDescriptor) {
				((CertDescriptor) cert).removeModifyListener(this);
			}
			fireCertificateRemoved(cert);
		}
	}

	public ICertificateDescriptor[] getCertificates() {
		return (ICertificateDescriptor[]) certificates.toArray(new ICertificateDescriptor[certificates.size()]);
	}

	public void addListener(ICertificateListener listener) {
		if (listeners == null) {
			listeners = new ArrayList();
		}
		listeners.add(listener);
	}

	public void removeListener(ICertificateListener listener) {
		if (listeners == null) {
			return;
		}
		listeners.remove(listener);
	}

	public void certificateChanged(ICertificateDescriptor cert) {
		if (certificates.contains(cert)) {
			fireCertificateChanged(cert);
		}
	}

	private void fireCertificateAdded(ICertificateDescriptor cert) {
		if (listeners == null) {
			return;
		}
		Iterator iterator = listeners.iterator();
		while (iterator.hasNext()) {
			((ICertificateListener) iterator.next()).certificateAdded(cert);
		}
	}

	private void fireCertificateChanged(ICertificateDescriptor cert) {
		if (listeners == null) {
			return;
		}
		Iterator iterator = listeners.iterator();
		while (iterator.hasNext()) {
			((ICertificateListener) iterator.next()).certificateChanged(cert);
		}
	}

	private void fireCertificateRemoved(ICertificateDescriptor cert) {
		if (listeners == null) {
			return;
		}
		Iterator iterator = listeners.iterator();
		while (iterator.hasNext()) {
			((ICertificateListener) iterator.next()).certificateRemoved(cert);
		}
	}

	private void fireStorageRefreshed() {
		if (listeners == null) {
			return;
		}
		Iterator iterator = listeners.iterator();
		while (iterator.hasNext()) {
			((ICertificateListener) iterator.next()).certificateStorageRefreshed();
		}
	}

	public void performDefaults() {
		certificates.clear();
		fireStorageRefreshed();
	}

	public void save() {
		CertManagerPlugin plugin = CertManagerPlugin.getDefault();
		if (plugin == null) {
			return;
		}
		IPreferenceStore store = plugin.getPreferenceStore();
		ISecurePreferences root = SecurePreferencesFactory.getDefault();
		ISecurePreferences secureStore = (root != null) ? root.node(SECURE_STORAGE_NODE) : null;
		int oldCount = store.getInt(ATTR_CERT_COUNT);
		int newCount = certificates.size();
		if (oldCount > newCount) {
			for (int i = oldCount - newCount; i < oldCount; i++) {
				deleteCertificate(store, secureStore, i);
			}
		}
		store.setValue(ATTR_CERT_COUNT, newCount);
		store.setValue(ATTR_CERT_LAST_UID, lastUid);
		Iterator iterator = certificates.iterator();
		int id = 0;
		while (iterator.hasNext()) {
			ICertificateDescriptor cert = (ICertificateDescriptor) iterator.next();
			saveCertificate(store, secureStore, cert, id);
			id++;
		}
	}

	private void load() {
		CertManagerPlugin plugin = CertManagerPlugin.getDefault();
		if (plugin == null) {
			return;
		}
		IPreferenceStore store = plugin.getPreferenceStore();
		ISecurePreferences root = SecurePreferencesFactory.getDefault();
		ISecurePreferences secureStore = (root != null) ? root.node(SECURE_STORAGE_NODE) : null;
		int count = store.getInt(ATTR_CERT_COUNT);
		lastUid = store.getLong(ATTR_CERT_LAST_UID);
		certificates.clear();
		for (int i = 0; i < count; i++) {
			CertDescriptor cert = loadCertificate(store, secureStore, i);
			certificates.add(cert);
			cert.addModifyListener(this);
		}
	}

	private static void saveCertificate(IPreferenceStore store, ISecurePreferences secureStore, ICertificateDescriptor cert, int id) {
		store.setValue(ATTR_CERT_ALIAS + id, getNonNullValue(cert.getAlias()));
		store.setValue(ATTR_CERT_LOCATION + id, getNonNullValue(cert.getStoreLocation()));
		store.setValue(ATTR_CERT_TYPE + id, getNonNullValue(cert.getStoreType()));
		store.setValue(ATTR_CERT_UID + id, getNonNullValue(cert.getUid()));
		if (secureStore != null) {
			try {
				secureStore.put(ATTR_CERT_PASS + id, getNonNullValue(cert.getStorePass()), true);
			} catch (StorageException e) {
				CertManagerPlugin.error("Secure storage error", e); //$NON-NLS-1$
			}
		}
	}

	private static CertDescriptor loadCertificate(IPreferenceStore store, ISecurePreferences secureStore, int id) {
		String uid = store.getString(ATTR_CERT_UID + id);
		CertDescriptor cert = new CertDescriptor(uid);
		cert.setAlias(store.getString(ATTR_CERT_ALIAS + id));
		cert.setStoreLocation(store.getString(ATTR_CERT_LOCATION + id));
		cert.setStoreType(store.getString(ATTR_CERT_TYPE + id));
		if (secureStore != null) {
			try {
				cert.setStorePass(secureStore.get(ATTR_CERT_PASS + id, null));
			} catch (StorageException e) {
				CertManagerPlugin.error("Secure storage error", e); //$NON-NLS-1$
			}
		}
		return cert;
	}

	private static void deleteCertificate(IPreferenceStore store, ISecurePreferences secureStore, int id) {
		store.setToDefault(ATTR_CERT_ALIAS + id);
		store.setToDefault(ATTR_CERT_LOCATION + id);
		store.setToDefault(ATTR_CERT_TYPE + id);
		store.setToDefault(ATTR_CERT_UID + id);
		if (secureStore != null) {
			secureStore.remove(ATTR_CERT_PASS + id);
		}
	}

	private static String getNonNullValue(String value) {
		return (value == null) ? "" : value; //$NON-NLS-1$
	}
}
