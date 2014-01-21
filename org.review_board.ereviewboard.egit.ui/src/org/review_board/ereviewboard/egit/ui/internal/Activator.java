/*******************************************************************************
 * Copyright (c) 2011 Robert Munteanu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Robert Munteanu - initial API and implementation
 *******************************************************************************/
package org.review_board.ereviewboard.egit.ui.internal;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.osgi.service.localization.BundleLocalization;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.namespace.BundleNamespace;

/**
 * @author Robert Munteanu
 * 
 */
public class Activator extends Plugin {

    public static final String PLUGIN_ID = "org.review_board.ereviewboard.subclipse.ui";

    private static volatile Activator DEFAULT;
    
    private IPreferenceStore store;
    public void start(BundleContext context) throws Exception {

        super.start(context);
        store = new ScopedPreferenceStore(InstanceScope.INSTANCE, context.getBundle().getSymbolicName());
        DEFAULT = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {

        DEFAULT = null;
    }

    public static Activator getDefault() {

        return DEFAULT;
    }

    public void trace(TraceLocation location, String message) {

        if (!Platform.inDebugMode())
            return;

        String debugOption = Platform.getDebugOption(PLUGIN_ID + "/debug" + location.getPrefix());
        
        if ( !Boolean.parseBoolean(debugOption) ) 
            return;

        getLog().log(new Status(IStatus.INFO, PLUGIN_ID, message));
    }

    /**
     * 
     * @param severity one of the {@link IStatus} severity constants
     * @param message
     * @param cause the cause, can be <code>null</code>
     * 
     * @see #log(int, String)
     */
    public void log(int severity, String message, Throwable cause) {
        
        getLog().log(new Status(severity, PLUGIN_ID, message, cause));
    }
    
    /**
     * 
     * @param severity one of the {@link IStatus} severity constants
     * @param message 
     */
    public void log(int severity, String message) {
        
        log(severity, message, null);
    }

	public IPreferenceStore getPreferenceStore() {
		return store;
	}
}
