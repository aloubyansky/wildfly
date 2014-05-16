/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.operations;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.util.Base64;

/**
 * @author Alexey Loubyansky
 *
 */
public class DomainPatchRebootStepHandler implements OperationStepHandler {

    static byte[] createAuthKey() {
        final Random rng = new Random(new SecureRandom().nextLong());
        byte[] authKey = new byte[16];
        rng.nextBytes(authKey);
        return authKey;
    }

    static String[] createPatchingProcessCmd(final HostControllerEnvironment environment, final String modulePath, byte[] authKey) {
        final String bootModule = "org.jboss.as.patching.process";
        final List<String> command = createCommand(environment, bootModule, modulePath);

        String loggingConfiguration = System.getProperty("logging.configuration");
        if (loggingConfiguration == null) {
            loggingConfiguration = "file:" + environment.getDomainConfigurationDir().getAbsolutePath() + "/logging.properties";
        }
        command.add("-Dlogging.configuration=" + loggingConfiguration);
        command.add("--pc-address=" + environment.getProcessControllerAddress().getHostAddress());
        command.add("--pc-port=" + environment.getProcessControllerPort());
        command.add("--auth-key=" + Base64.encodeBytes(authKey));
        command.add("--wrk-dir=" + environment.getHomeDir().getAbsolutePath());
        command.add("--hc-cmd=" + createHCCommand(environment, modulePath));
        return command.toArray(new String[command.size()]);
    }

    static String createHCCommand(final HostControllerEnvironment environment, final String modulePath) {
        final String bootModule = "org.jboss.as.host-controller";
        final List<String> command = createCommand(environment, bootModule, modulePath);
        environment.getRawCommandLineArgs().append(command);
        command.add("-mp");
        command.add(modulePath);
        final String loggingConfiguration = System.getProperty("logging.configuration");
        if (loggingConfiguration != null) {
            command.add("-Dlogging.configuration=" + loggingConfiguration);
        }
        final StringBuilder buf = new StringBuilder();
        buf.append(command.get(0));
        for(int i = 1; i < command.size(); ++i) {
            buf.append('&').append(command.get(i));
        }
        return buf.toString();
    }

    static List<String> createCommand(final HostControllerEnvironment environment, final String bootModule, final String modulePath) {

        final String jvm = environment.getDefaultJVM().getAbsolutePath();
        final List<String> javaOptions = new ArrayList<String>();
        final String bootJar = new File(environment.getHomeDir(), "jboss-modules.jar").getAbsolutePath();
        final String logModule = "org.jboss.logmanager";
        final String jaxpModule = "javax.xml.jaxp-provider";

        return createCommand(jvm, javaOptions, bootJar, modulePath, logModule, jaxpModule, bootModule);
    }

    static List<String> createCommand(final String jvm, final List<String> javaOptions, String bootJar, String modulePath,
                                  String logModule, String jaxpModule, String bootModule) {
        final List<String> command = new ArrayList<String>();

        command.add(jvm);
        command.addAll(javaOptions);
        command.add("-jar");
        command.add(bootJar);
        command.add("-mp");
        command.add(modulePath);
        //command.add("-logmodule");
        //command.add(logModule);
        command.add("-jaxpmodule");
        command.add(jaxpModule);
        command.add(bootModule);
        command.add("-Djava.util.logging.manager=org.jboss.logmanager.LogManager");
        return command;
    }

    private HostControllerEnvironment hostEnvironment;
    private String modulePath;
    private ProcessControllerClient client;

    public DomainPatchRebootStepHandler(HostControllerEnvironment hostEnv, String modulePath, ProcessControllerClient client) {
        if(hostEnv == null) {
            throw new IllegalArgumentException("HostControllerEnvironment is null");
        }
        if(modulePath == null) {
            throw new IllegalArgumentException("Module path is null");
        }
        if(client == null) {
            throw new IllegalArgumentException("ProcessControllerClient is null");
        }
        this.hostEnvironment = hostEnv;
        this.modulePath = modulePath;
        this.client = client;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        context.acquireControllerLock();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final byte[] authKey = createAuthKey();
                final String[] cmd = createPatchingProcessCmd(hostEnvironment, modulePath, authKey);
                try {
                    client.addProcess("patching-process", authKey, cmd, hostEnvironment.getHomeDir().getAbsolutePath(), Collections.<String,String>emptyMap());
                    client.startProcess("patching-process");
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new OperationFailedException(new ModelNode().set("failed to launch patching-process"));
                }

                context.stepCompleted();
            }}, OperationContext.Stage.RUNTIME);
        context.getResult().set(new ModelNode("ok"));
        context.stepCompleted();
    }
}
