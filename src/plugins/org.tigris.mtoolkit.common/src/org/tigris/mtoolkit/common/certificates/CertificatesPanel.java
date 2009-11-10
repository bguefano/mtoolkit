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
package org.tigris.mtoolkit.common.certificates;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.tigris.mtoolkit.common.Messages;

public class CertificatesPanel {

	private static final String MTOOLKIT_PAGE_ID = "org.tigris.mtoolkit.common.preferences.MToolkitPreferencePage"; //$NON-NLS-1$

	private Group signContentGroup;
	private Button chkSignContent;
	private Label lblCertificates;
	private Table tblCertificates;

	public CertificatesPanel(Composite parent, int horizontalSpan, int verticalSpan) {
		// Signing content group
		signContentGroup = new Group(parent, SWT.NONE);
		signContentGroup.setText(Messages.CertificatesPanel_signContentGroup);
		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = horizontalSpan;
		gridData.verticalSpan = verticalSpan;
		signContentGroup.setLayoutData(gridData);
		signContentGroup.setLayout(new GridLayout());

		chkSignContent = new Button(signContentGroup, SWT.CHECK);
		chkSignContent.setLayoutData(new GridData());
		chkSignContent.setText(Messages.CertificatesPanel_chkSignContent);
		chkSignContent.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				tblCertificates.setEnabled(chkSignContent.getSelection());
			}
		});

		lblCertificates = new Label(signContentGroup, SWT.NONE);
		lblCertificates.setText(Messages.CertificatesPanel_lblCertificates);
		lblCertificates.setLayoutData(new GridData());

		// Certificates table
		int style = SWT.SINGLE | SWT.CHECK | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION;
		tblCertificates = new Table(signContentGroup, style);
		gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.heightHint = 50;
		tblCertificates.setLayoutData(gridData);
		tblCertificates.setLinesVisible(true);
		tblCertificates.setHeaderVisible(true);
		TableColumn column = new TableColumn(tblCertificates, SWT.LEFT);
		column.setText(Messages.CertificatesPanel_tblCertColAlias);
		column.setWidth(100);
		column = new TableColumn(tblCertificates, SWT.LEFT);
		column.setText(Messages.CertificatesPanel_tblCertColLocation);
		column.setWidth(160);
	}

	public void initialize(List signUids) {
		ICertificateDescriptor certificates[] = CertUtils.getCertificates();
		if (certificates == null || certificates.length == 0) {
			setNoCertificatesAvailable();
		}
		boolean foundCert = false;
		for (int i = 0; i < certificates.length; i++) {
			TableItem item = new TableItem(tblCertificates, SWT.NONE);
			item.setText(0, certificates[i].getAlias());
			item.setText(1, certificates[i].getStoreLocation());
			item.setData(certificates[i].getUid());
			if (signUids != null && signUids.contains(certificates[i].getUid())) {
				item.setChecked(true);
				foundCert = true;
			}
		}
		tblCertificates.setEnabled(foundCert);
		chkSignContent.setSelection(foundCert);
	}

	public List getSignCertificateUids() {
		List signUids = new ArrayList();
		if (chkSignContent.getSelection()) {
			TableItem items[] = tblCertificates.getItems();
			for (int i = 0; i < items.length; i++) {
				if (items[i].getChecked()) {
					signUids.add(items[i].getData());
				}
			}
		}
		return signUids;
	}

	private void setNoCertificatesAvailable() {
		setCertificateControlsVisible(false);

		final Link link = new Link(signContentGroup, SWT.NONE);
		link.setLayoutData(new GridData());
		link.setText(Messages.CertificatesPanel_lblNoCertificates);
		link.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Shell shell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
				PreferencesUtil.createPreferenceDialogOn(shell, MTOOLKIT_PAGE_ID, null, null).open();
				ICertificateDescriptor certificates[] = CertUtils.getCertificates();
				if (certificates == null || certificates.length == 0) {
					return;
				}
				link.dispose();
				setCertificateControlsVisible(true);
				layoutControls();
				initialize(null);
			}
		});

		signContentGroup.layout();
	}

	private void setCertificateControlsVisible(boolean visible) {
		chkSignContent.setSelection(visible);
		chkSignContent.setEnabled(visible);

		lblCertificates.setVisible(visible);
		((GridData) lblCertificates.getLayoutData()).exclude = !visible;

		tblCertificates.setVisible(visible);
		((GridData) tblCertificates.getLayoutData()).exclude = !visible;
	}

	private void layoutControls() {
		signContentGroup.layout();

		Composite parent = signContentGroup;
		while (parent != null) {
			if (parent instanceof Shell) {
				Shell shell = (Shell) parent;
				Point size = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				shell.setBounds(shell.getLocation().x, shell.getLocation().y, size.x, size.y);
				break;
			}
			parent = parent.getParent();
		}
	}
}
