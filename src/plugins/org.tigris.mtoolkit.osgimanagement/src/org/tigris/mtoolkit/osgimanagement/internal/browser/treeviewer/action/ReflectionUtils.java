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
package org.tigris.mtoolkit.osgimanagement.internal.browser.treeviewer.action;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtils {

	public static class InvocationException extends Exception {

		private static final long serialVersionUID = -1277607279597799415L;

		public InvocationException() {
			super();
		}

		public InvocationException(String message, Throwable cause) {
			super(message, cause);
		}

		public InvocationException(String message) {
			super(message);
		}

		public InvocationException(Throwable cause) {
			super(cause);
		}

	}

	public static Object invokeMethod(Object obj, String methodName, Class[] parameterTypes, Object[] parameterValues)
					throws InvocationException {
		if (obj == null)
			throw new NullPointerException();
		Class clazz = obj.getClass();
		try {
			Method m = clazz.getMethod(methodName, parameterTypes);
			Object result = m.invoke(obj, parameterValues);
			return result;
		} catch (SecurityException e) {
			throw new InvocationException(e);
		} catch (NoSuchMethodException e) {
			throw new InvocationException(e);
		} catch (IllegalArgumentException e) {
			throw new InvocationException(e);
		} catch (IllegalAccessException e) {
			throw new InvocationException(e);
		} catch (InvocationTargetException e) {
			throw new InvocationException(e.getTargetException());
		}
	}

	public static Object invokeMethod(Object obj, String methodName) throws InvocationException {
		return invokeMethod(obj, methodName, null, null);
	}

	public static Object invokeStaticMethod(String className, String methodName, Class[] parameterTypes,
					Object[] parameterValues) throws InvocationException {
		Class cl;
		try {
			cl = ReflectionUtils.class.getClassLoader().loadClass(className);
			Method method = cl.getMethod(methodName, parameterTypes);
			return method.invoke(null, parameterValues);
		} catch (ClassNotFoundException e) {
			throw new InvocationException(e);
		} catch (SecurityException e) {
			throw new InvocationException(e);
		} catch (NoSuchMethodException e) {
			throw new InvocationException(e);
		} catch (IllegalArgumentException e) {
			throw new InvocationException(e);
		} catch (IllegalAccessException e) {
			throw new InvocationException(e);
		} catch (InvocationTargetException e) {
			throw new InvocationException(e.getTargetException());
		}
	}

	public static Object invokeStaticMethod(String className, String methodName) throws InvocationException {
		return invokeStaticMethod(className, methodName, null, null);
	}

	public static Object newInstance(String className, Class[] parameterTypes, Object[] parameterValues)
					throws InvocationException {
		try {
			Class clazz = ReflectionUtils.class.getClassLoader().loadClass(className);
			Constructor c = clazz.getConstructor(parameterTypes);
			Object obj = c.newInstance(parameterValues);
			return obj;
		} catch (ClassNotFoundException e) {
			throw new InvocationException(e);
		} catch (SecurityException e) {
			throw new InvocationException(e);
		} catch (NoSuchMethodException e) {
			throw new InvocationException(e);
		} catch (IllegalArgumentException e) {
			throw new InvocationException(e);
		} catch (InstantiationException e) {
			throw new InvocationException(e);
		} catch (IllegalAccessException e) {
			throw new InvocationException(e);
		} catch (InvocationTargetException e) {
			throw new InvocationException(e.getTargetException());
		}
	}
}
