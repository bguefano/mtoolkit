/*******************************************************************************
 * Copyright (c) 2005, 2012 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.common.gui;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

// IPLOG: Parts of this class were got from the original StatusDialog
public class StatusLineDialog extends TrayDialog {
  private String     dialogTitle;

  private StatusLine statusLine;
  private IStatus    fLastStatus;
  private Button     fOkButton;

  private boolean    shellInitialized = false;

  public StatusLineDialog(Shell shell, String title) {
    super(shell);
    Assert.isNotNull(title);
    this.dialogTitle = title;
  }

  public IStatus getStatus() {
    return fLastStatus;
  }

  public void updateStatus(IStatus status) {
    if (fLastStatus != null && fLastStatus.equals(status)) {
      return;
    }
    fLastStatus = status;
    updateButtonsEnableState(status);
    if (statusLine != null) {
      Point oldSize = statusLine.getSize();

      statusLine.updateStatus(status);

      if (shellInitialized) { // only resize the shell if we have already
        // showed it, otherwise do nothing, it will
        // display the shell with correct size
        Point newSize = statusLine.computeSize(oldSize.x, SWT.DEFAULT);
        Point shellSize = getShell().getSize();
        getShell().setSize(shellSize.x, shellSize.y + (newSize.y - oldSize.y));
        getShell().layout(true, true);
      }
    }
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
   */
  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText(dialogTitle);
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.window.Window#getShellStyle()
   */
  @Override
  protected int getShellStyle() {
    return super.getShellStyle() | SWT.RESIZE;
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.dialogs.TrayDialog#createButtonBar(org.eclipse.swt.widgets.Composite)
   */
  @Override
  protected Control createButtonBar(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);

    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    layout.marginHeight = 0;
    layout.marginLeft = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
    layout.marginWidth = 0;
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

    statusLine = new StatusLine(composite, SWT.NONE);
    GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false);

    if (isHelpAvailable()) {
      statusData.horizontalSpan = 2;
      createHelpControl(composite);
    }
    statusLine.setLayoutData(statusData);
    applyDialogFont(composite);

    /*
     * Create the rest of the button bar, but tell it not to create a help
     * button (we've already created it).
     */
    boolean helpAvailable = isHelpAvailable();
    setHelpAvailable(false);
    super.createButtonBar(composite);
    setHelpAvailable(helpAvailable);
    return composite;
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.dialogs.Dialog#createContents(org.eclipse.swt.widgets.Composite)
   */
  @Override
  protected Control createContents(Composite parent) {
    Control control = super.createContents(parent);
    refresh();
    if (fLastStatus != null) {
      IStatus safeStatus = new Status(fLastStatus.getSeverity(), fLastStatus.getPlugin(), fLastStatus.getCode(),
          "", fLastStatus.getException()); //$NON-NLS-1$
      updateStatus(safeStatus);
    }
    return control;
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.dialogs.Dialog#initializeBounds()
   */
  @Override
  protected void initializeBounds() {
    super.initializeBounds();
    shellInitialized = true;
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.dialogs.Dialog#createButtonsForButtonBar(org.eclipse.swt.widgets.Composite)
   */
  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    fOkButton = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    getShell().setDefaultButton(fOkButton);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    if (fLastStatus != null) {
      updateButtonsEnableState(fLastStatus);
    }
  }

  /* (non-Javadoc)
   * @see org.eclipse.jface.dialogs.Dialog#buttonPressed(int)
   */
  @Override
  protected void buttonPressed(int buttonId) {
    if (IDialogConstants.OK_ID == buttonId) {
      IStatus status = getStatus();
      if (checkStatus(status)) {
        commit();
      }
      // recheck dialog status and cancel if not OK
      if (!checkStatus(status)) {
        return;
      }
    }
    super.buttonPressed(buttonId);
  }

  protected void updateButtonsEnableState(IStatus status) {
    if (fOkButton != null && !fOkButton.isDisposed()) {
      fOkButton.setEnabled(!status.matches(IStatus.ERROR));
      getShell().setDefaultButton(fOkButton);
    }
  }

  protected void setTextField(Text field, String value) { // NO_UCD
    field.setText(value == null ? "" : value); //$NON-NLS-1$
  }

  protected void commit() {
  }

  protected void refresh() {
  }

  private boolean checkStatus(IStatus status) {
    return status.isOK() || (!status.matches(IStatus.ERROR) && status.matches(IStatus.WARNING));
  }
}
