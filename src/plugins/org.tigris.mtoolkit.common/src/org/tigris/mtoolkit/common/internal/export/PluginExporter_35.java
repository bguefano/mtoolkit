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
package org.tigris.mtoolkit.common.internal.export;

import java.io.File;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.core.exports.FeatureExportInfo;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEPluginImages;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressConstants;
import org.tigris.mtoolkit.common.Messages;
import org.tigris.mtoolkit.common.PluginUtilities;
import org.tigris.mtoolkit.common.ReflectionUtils;
import org.tigris.mtoolkit.common.ReflectionUtils.InvocationException;
import org.tigris.mtoolkit.common.UtilitiesPlugin;

/**
 *
 * @noextend This class is not intended to be subclassed by clients.
 *
 */
public class PluginExporter_35 extends BasePluginExporter {
  /**
   * @since 5.0
   */
  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.common.internal.export.IPluginExporter#asyncExportPlugins(java.lang.Object)
   */
  public void asyncExportPlugins(Object info) {
    try {
      final FeatureExportInfo fInfo = (FeatureExportInfo) info;
      final Object op = createExportOperation(fInfo);
      ((Job) op).addJobChangeListener(new JobChangeAdapter() {
        /* (non-Javadoc)
         * @see org.eclipse.core.runtime.jobs.JobChangeAdapter#done(org.eclipse.core.runtime.jobs.IJobChangeEvent)
         */
        @Override
        public void done(IJobChangeEvent event) {
          setResult(handleOperationResult(fInfo, op, event.getResult()));
        }
      });
      ((Job) op).schedule();
    } catch (ReflectionUtils.InvocationException e) {
      setResult(new Status(IStatus.ERROR, UtilitiesPlugin.PLUGIN_ID, Messages.plugin_exporter_not_compatible, e));
    }
  }

  public static boolean isCompatible() {
    return PluginUtilities.compareVersion("org.eclipse.pde.ui", PluginUtilities.VERSION_3_5_0); //$NON-NLS-1$
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.common.internal.export.IPluginExporter#getQualifier()
   */
  public String getQualifier() {
    try {
      return (String) ReflectionUtils.invokeStaticMethod("org.eclipse.pde.internal.build.site.QualifierReplacer", //$NON-NLS-1$
          "getDateQualifier"); //$NON-NLS-1$
    } catch (Throwable t) {
      return "qualifier"; //$NON-NLS-1$
    }
  }

  /**
   * @since 5.0
   */
  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.common.internal.export.IPluginExporter#syncExportPlugins(java.lang.Object, org.eclipse.core.runtime.IProgressMonitor)
   */
  public IStatus syncExportPlugins(Object info, IProgressMonitor monitor) {
    try {
      final FeatureExportInfo fInfo = (FeatureExportInfo) info;
      final Object op = createExportOperation(fInfo);
      IStatus result = Status.OK_STATUS;
      try {
        Job.getJobManager().beginRule(((Job) op).getRule(), monitor);
        result = (IStatus) ReflectionUtils.invokeProtectedMethod(op, "run", new Class[] {
          IProgressMonitor.class
        }, new Object[] {
          monitor
        });
      } catch (ReflectionUtils.InvocationException e) {
        result = UtilitiesPlugin.newStatus(IStatus.ERROR, Messages.plugin_exporter_not_compatible, e);
      } catch (ThreadDeath e) {
        throw e;
      } catch (Throwable t) {
        result = UtilitiesPlugin.newStatus(IStatus.ERROR, "An internal error ocurred during: " + ((Job) op).getName(),
            t);
      } finally {
        Job.getJobManager().endRule(((Job) op).getRule());
      }
      return handleOperationResult(fInfo, op, result);
    } catch (ReflectionUtils.InvocationException e) {
      return new Status(IStatus.ERROR, UtilitiesPlugin.PLUGIN_ID, Messages.plugin_exporter_not_compatible, e);
    }
  }

  private Object createExportOperation(final FeatureExportInfo fInfo) throws InvocationException {
    // always allow binary cucles
    ReflectionUtils.setField(fInfo, "allowBinaryCycles", Boolean.TRUE);
    final Object op = ReflectionUtils.newInstance(
        "org.eclipse.pde.internal.core.exports.PluginExportOperation", new Class[] { //$NON-NLS-1$
            FeatureExportInfo.class, String.class
        }, new Object[] {
            fInfo, ""
        });
    ((Job) op).setUser(true);
    ((Job) op).setRule(ResourcesPlugin.getWorkspace().getRoot());
    ((Job) op).setProperty(IProgressConstants.ICON_PROPERTY, PDEPluginImages.DESC_PLUGIN_OBJ);
    return op;
  }

  private IStatus handleOperationResult(final FeatureExportInfo info, Object operation, IStatus result) {
    boolean errors = false;
    try {
      errors = ((Boolean) ReflectionUtils.invokeMethod(operation, "hasAntErrors")).booleanValue(); //$NON-NLS-1$
      if (errors) {
        final File logLocation = new File(info.destinationDirectory, "logs.zip"); //$NON-NLS-1$
        if (logLocation.exists()) {
          Display display = PlatformUI.getWorkbench().getDisplay();
          display.syncExec(new Runnable() {
            /* (non-Javadoc)
             * @see java.lang.Runnable#run()
             */
            public void run() {
              ExportErrorDialog dialog = new ExportErrorDialog("Problem during export", logLocation); //$NON-NLS-1$
              dialog.open();
            }
          });
        }
      }
    } catch (ReflectionUtils.InvocationException t) {
      UtilitiesPlugin.error("Failed to get export plugins status", t); //$NON-NLS-1$
    } finally {
      if (errors && result.isOK()) {
        // if the job finished correctly, but there are ant errors
        return new Status(IStatus.ERROR, PDEPlugin.getPluginId(), NLS.bind(
            "Errors occurred during the export operation. The ant tasks generated log files which can be found at {0}",
            info.destinationDirectory));
      }
    }
    return result;
  }
}