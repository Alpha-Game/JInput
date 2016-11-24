/*
 * %W% %E%
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
/*****************************************************************************
 * Copyright (c) 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistribution of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materails provided with the distribution.
 *
 * Neither the name Sun Microsystems, Inc. or the names of the contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind.
 * ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANT OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NON-INFRINGEMEN, ARE HEREBY EXCLUDED.  SUN MICROSYSTEMS, INC. ("SUN") AND
 * ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS
 * A RESULT OF USING, MODIFYING OR DESTRIBUTING THIS SOFTWARE OR ITS 
 * DERIVATIVES.  IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES.  HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OUR INABILITY TO USE THIS SOFTWARE,
 * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed or intended for us in
 * the design, construction, operation or maintenance of any nuclear facility
 *
 *****************************************************************************/
package net.java.games.input;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import net.java.games.util.plugins.*;

/**
 * The default controller environment.
 *
 * @version %I% %G%
 * @author Michael Martak
 */
public class DefaultControllerEnvironment extends ControllerEnvironment implements ControllerListener {
	static String libPath;
	
	private static Logger log = Logger.getLogger(DefaultControllerEnvironment.class.getName());
	
	/**
	 * Static utility method for loading native libraries.
	 * It will try to load from either the path given by
	 * the net.java.games.input.librarypath property
	 * or through System.loadLibrary().
	 * 
	 */
	static void loadLibrary(final String lib_name) {
		AccessController.doPrivileged(
				new PrivilegedAction<Object>() {
					public final Object run() {
						String lib_path = System.getProperty("net.java.games.input.librarypath");
						if (lib_path != null)
							System.load(lib_path + File.separator + System.mapLibraryName(lib_name));
						else
							System.loadLibrary(lib_name);
						return null;
					}
				});
	}
    
	static String getPrivilegedProperty(final String property) {
       return (String)AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    return System.getProperty(property);
                }
            });
	}
		
	static String getPrivilegedProperty(final String property, final String default_value) {
       return (String)AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    return System.getProperty(property, default_value);
                }
            });
	}
		  
	private Collection<ControllerEnvironment> loadedPlugins;
	private List<Controller> controllersAdded;
	private List<Controller> controllersRemoved;

    /**
     * Public no-arg constructor.
     */
    public DefaultControllerEnvironment() {
    	loadedPlugins = new ArrayList<ControllerEnvironment>();
    	controllersAdded = new LinkedList<Controller>();
		controllersRemoved = new LinkedList<Controller>();
    }
        
    /* This is jeff's new plugin code using Jeff's Plugin manager */
    private void scanControllers() {
        String pluginPathName = getPrivilegedProperty("jinput.controllerPluginPath");
        if(pluginPathName == null) {
            pluginPathName = "controller";
        }
        
        scanControllersAt(getPrivilegedProperty("java.home") +
            File.separator + "lib"+File.separator + pluginPathName);
        scanControllersAt(getPrivilegedProperty("user.dir")+
            File.separator + pluginPathName);
    }
    
    private void scanControllersAt(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        try {
            Plugins plugins = new Plugins(file);
            Class<ControllerEnvironment>[] envClasses = plugins.getExtends(ControllerEnvironment.class);
            for(int i=0;i<envClasses.length;i++){
                try {
					ControllerEnvironment.logln("ControllerEnvironment "+
                            envClasses[i].getName()
                            +" loaded by "+envClasses[i].getClassLoader());
                    ControllerEnvironment ce = (ControllerEnvironment)
                    	envClasses[i].newInstance();
					if(ce.isSupported()) {
						loadedPlugins.add(ce);
						ce.addControllerListener(this);
					} else {
						logln(envClasses[i].getName() + " is not supported");
					}
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
           
	public boolean isSupported() {
		return true;
	}

	@Override
	public void controllerRemoved(ControllerEvent ev) {
		controllersRemoved.add(ev.getController());
	}

	@Override
	public void controllerAdded(ControllerEvent ev) {
		controllersAdded.add(ev.getController());
	}

	@Override
	protected void updateControllers() {
		for (ControllerEnvironment ce : loadedPlugins) {
    		ce.updateControllers();
    	}
		_updateControllers();
	}
	
	private void _updateControllers() {
		// Add/Remove controllers
		synchronized(controllers) {
			for (Controller controller : controllersRemoved) {
				controllers.remove(controller.getInstanceIdentifier());
			}
			for (Controller controller : controllersAdded) {
				controllers.put(controller.getInstanceIdentifier(), controller);
			}
		}
		
		// Fire remove controllers events
		for (Controller controller : controllersRemoved) {
			fireControllerRemoved(controller);
		}
		controllersRemoved.clear();
		
		// Fire add controller events
		for (Controller controller : controllersAdded) {
			fireControllerAdded(controller);
		}
		controllersAdded.clear();
	}

	@Override
	protected void initialize() {
		// Controller list has not been scanned.
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                scanControllers();
                return null;
            }
        });
        //Check the properties for specified controller classes
        String pluginClasses = getPrivilegedProperty("jinput.plugins", "") + " " + getPrivilegedProperty("net.java.games.input.plugins", "");
		if(!getPrivilegedProperty("jinput.useDefaultPlugin", "true").toLowerCase().trim().equals("false") && !getPrivilegedProperty("net.java.games.input.useDefaultPlugin", "true").toLowerCase().trim().equals("false")) {
			String osName = getPrivilegedProperty("os.name", "").trim();
			if(osName.equals("Linux")) {
				pluginClasses = pluginClasses + " net.java.games.input.LinuxEnvironmentPlugin";
			} else if(osName.equals("Mac OS X")) {
				pluginClasses = pluginClasses + " net.java.games.input.OSXEnvironmentPlugin";
			} else  if(osName.equals("Windows XP") || osName.equals("Windows Vista") || osName.equals("Windows 7")) {
				pluginClasses = pluginClasses + " net.java.games.input.DirectAndRawInputEnvironmentPlugin";
			} else if(osName.equals("Windows 98") || osName.equals("Windows 2000")) {
				pluginClasses = pluginClasses + " net.java.games.input.DirectInputEnvironmentPlugin";
			} else if (osName.startsWith("Windows")) {
				log.warning("Found unknown Windows version: " + osName);
				log.warning("Attempting to use default windows plug-in.");
				pluginClasses = pluginClasses + " net.java.games.input.DirectAndRawInputEnvironmentPlugin";
			} else {
				log.warning("Trying to use default plugin, OS name " + osName +" not recognised");
			}
		}

		StringTokenizer pluginClassTok = new StringTokenizer(pluginClasses, " \t\n\r\f,;:");
		while(pluginClassTok.hasMoreTokens()) {
			String className = pluginClassTok.nextToken();					
			try {
				if(!loadedPlugins.contains(className)) {
					log.fine("Loading: " + className);
					Class<?> ceClass = Class.forName(className);						
					ControllerEnvironment ce = (ControllerEnvironment) ceClass.newInstance();
					if(ce.isSupported()) {
						loadedPlugins.add(ce);
						
					} else {
						logln(ceClass.getName() + " is not supported");
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		
		for (ControllerEnvironment ce : loadedPlugins) {
			ce.addControllerListener(this);
    		ce.initialize();
    	}
		
		_updateControllers();
	}

	@Override
	protected void destroy() {
		for (ControllerEnvironment ce : loadedPlugins) {
    		ce.removeControllerListener(this);
    		ce.destroy();
    	}
	}
}
