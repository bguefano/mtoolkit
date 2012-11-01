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
package org.tigris.mtoolkit.iagent.mbsa;

public interface MBSAConstants {

  public static final int IAGENT_CMD_PING            = 0x12121212;

  public static final int IAGENT_RES_OK              = 0x00000000;

  public static final int IAGENT_RES_INTERNAL_ERROR  = 0x00000010;

  public static final int IAGENT_RES_UNKNOWN_COMMAND = 0x0001ffff;

  public static final int IAGENT_FLAGS_RESULT        = 0x80000000;

}
