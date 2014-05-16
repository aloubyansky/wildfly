/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.host.controller;

import org.jboss.as.process.CommandLineConstants;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The unprocessed command line arguments, used when recreating the host-controller process.
 *
 * @author Emanuel Muckenhuber
 */
public class RawCommandLineArgs {

    private boolean backupDC;
    private boolean cachedDC;
    private String defaultJVM;
    private String domainConfig;
    private String hostConfig;
    private String properties;
    private String processControllerAddress;
    private Integer processControllerPort;
    private String hostControllerAddress;
    private Integer hostControllerPort;
    private final Set<String> bindings = new HashSet<String>();
    private final Map<String, String> sysProperties = new HashMap<String, String>();

    public boolean isBackupDC() {
        return backupDC;
    }

    public void setBackupDC(boolean backupDC) {
        this.backupDC = backupDC;
    }

    public boolean isCachedDC() {
        return cachedDC;
    }

    public void setCachedDC(boolean cachedDC) {
        this.cachedDC = cachedDC;
    }

    public String getDefaultJVM() {
        return defaultJVM;
    }

    public void setDefaultJVM(String defaultJVM) {
        this.defaultJVM = defaultJVM;
    }

    public String getDomainConfig() {
        return domainConfig;
    }

    public void setDomainConfig(String domainConfig) {
        this.domainConfig = domainConfig;
    }

    public String getHostConfig() {
        return hostConfig;
    }

    public void setHostConfig(String hostConfig) {
        this.hostConfig = hostConfig;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getProcessControllerAddress() {
        return processControllerAddress;
    }

    public void setProcessControllerAddress(String processControllerAddress) {
        this.processControllerAddress = processControllerAddress;
    }

    public Integer getProcessControllerPort() {
        return processControllerPort;
    }

    public void setProcessControllerPort(Integer processControllerPort) {
        this.processControllerPort = processControllerPort;
    }

    public String getHostControllerAddress() {
        return hostControllerAddress;
    }

    public void setHostControllerAddress(String hostControllerAddress) {
        this.hostControllerAddress = hostControllerAddress;
    }

    public Integer getHostControllerPort() {
        return hostControllerPort;
    }

    public void setHostControllerPort(Integer hostControllerPort) {
        this.hostControllerPort = hostControllerPort;
    }

    public Set<String> getBindings() {
        return bindings;
    }

    public Map<String, String> getSysProperties() {
        return sysProperties;
    }

    public void append(final Collection<String> command) {

        if(properties != null) {
            command.add(CommandLineConstants.PROPERTIES);
            command.add(properties);
        }
        if(processControllerAddress != null) {
            command.add(CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR);
            command.add(processControllerAddress);
        }
        if(processControllerPort != null) {
            command.add(CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT);
            command.add("" + processControllerPort);
        }
        if(hostControllerAddress != null) {
            command.add(CommandLineConstants.INTERPROCESS_HC_ADDRESS);
            command.add(hostControllerAddress);
        }
        if(hostControllerPort != null) {
            command.add(CommandLineConstants.INTERPROCESS_HC_PORT);
            command.add("" + hostControllerPort);
        }
        if(backupDC) {
            command.add(CommandLineConstants.BACKUP_DC);
        }
        if(cachedDC) {
            command.add(CommandLineConstants.CACHED_DC);
        }
        if(defaultJVM != null) {
            command.add(CommandLineConstants.DEFAULT_JVM);
            command.add(defaultJVM);
        }
        if(domainConfig != null) {
            command.add(CommandLineConstants.DOMAIN_CONFIG);
            command.add(domainConfig);
        }
        if(hostConfig != null) {
            command.add(CommandLineConstants.HOST_CONFIG);
            command.add(hostConfig);
        }
        if(! sysProperties.isEmpty()) {
            for(final Map.Entry<String, String> entry : sysProperties.entrySet()) {
                command.add(CommandLineConstants.SYS_PROP + entry.getKey() + "=" + entry.getValue());
            }
        }
        if(! bindings.isEmpty()) {
            for(final String binding : bindings) {
                command.add(binding);
            }
        }
    }

}