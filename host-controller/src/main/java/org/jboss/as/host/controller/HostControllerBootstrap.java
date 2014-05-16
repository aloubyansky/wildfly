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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.patching.process.InputHandler;
import org.jboss.as.patching.process.InputOutputHandlerFactory;
import org.jboss.as.patching.process.PatchingMessenger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Bootstrap of the HostController process.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HostControllerBootstrap {

    private final ShutdownHook shutdownHook;
    private final ServiceContainer serviceContainer;
    private final HostControllerEnvironment environment;
    private final byte[] authCode;

    public HostControllerBootstrap(final HostControllerEnvironment environment, final byte[] authCode) {
        this.environment = environment;
        this.authCode = authCode;
        this.shutdownHook = new ShutdownHook();
        this.serviceContainer = shutdownHook.register();
    }

    /**
     * Start the host controller services.
     *
     * @throws Exception
     */
    public void bootstrap(InputStream input) throws Exception {

        final HostRunningModeControl runningModeControl = environment.getRunningModeControl();
        final ControlledProcessState processState = new ControlledProcessState(true);
        shutdownHook.setControlledProcessState(processState);
        ServiceTarget target = serviceContainer.subTarget();
        ControlledProcessStateService.addService(target, processState);
        final HostControllerService hcs = new HostControllerService(environment, runningModeControl, authCode, processState);
        target.addService(HostControllerService.HC_SERVICE_NAME, hcs).install();
        HostControllerAgentService.addService(target, input);
    }

    public static class HostControllerAgentService implements Service<String> {

        public static final ServiceName HC_AGENT_NAME = ServiceName.JBOSS.append("host-controller", "agent");

        static void addService(ServiceTarget target, InputStream input) {
            final HostControllerAgentService agent = new HostControllerAgentService(input);
            target.addService(HostControllerAgentService.HC_AGENT_NAME, agent)
            .addDependency(ProcessControllerConnectionService.SERVICE_NAME, ProcessControllerConnectionService.class, agent.injectedProcessControllerConnection)
            .install();
        }

        private final InputStream input;
        private final InjectedValue<ProcessControllerConnectionService> injectedProcessControllerConnection = new InjectedValue<ProcessControllerConnectionService>();
        private Thread receiver;

        public HostControllerAgentService(InputStream input) {
            if(input == null) {
                throw new IllegalArgumentException("Input is null");
            }
            this.input = input;
        }

        @Override
        public String getValue() throws IllegalStateException, IllegalArgumentException {
            return null;
        }

        @Override
        public void start(StartContext context) throws StartException {
            System.out.println("HC AGENT STARTING");

            receiver = new Thread(new Runnable() {
                @Override
                public void run() {
                    final InputHandler inputHandler = InputOutputHandlerFactory.getInputHandler(input);
                    final PatchingMessenger msgr = new PatchingMessenger(injectedProcessControllerConnection.getValue().getClient(), "patching-process");

                    try {
                        try {
                            msgr.send("HC AGENT is up");
                            System.out.println("HC AGENT sent the up notification");
                        } catch(IOException e) {
                            synchronized (input) {
                                System.out.println("NOTIFYING 2");
                                input.notify();
                            }
                            throw new IllegalStateException("Failed to obtain output stream for the patching process.");
                        }

                        System.out.println("HC AGENT waiting for input");
                        while (inputHandler.waitUntilAvailable()) {
                            final String msg = inputHandler.unmarshalNext();
                            System.out.println("HC AGENT received " + msg);

                            if("PatchingProcess sais quit".equals(msg)) {
                                break;
                            } else {
                                try {
                                    msgr.send("HC AGENT: i see you're saying '" + msg + "' but may i quit, pls?");
                                } catch(IOException e) {
                                    synchronized (input) {
                                        System.out.println("NOTIFYING 1");
                                        input.notify();
                                    }
                                    throw new IllegalStateException("Failed to obtain output stream for the patching process.");
                                }
                            }
                        }
                        System.out.println("HC AGENT done receiving");
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                    synchronized(input){
                        System.out.println("HC AGENT notifying");
                        input.notify();
                    }
                }}, "hc-agent");
            receiver.start();
            System.out.println("HC AGENT STARTED");
        }

        @Override
        public void stop(StopContext context) {
            System.out.println("HC AGENT STOPPED");
            receiver.interrupt();
        }
    }

    private static class ShutdownHook extends Thread {
        private boolean down;
        private ControlledProcessState processState;
        private ServiceContainer container;

        private ServiceContainer register() {

            Runtime.getRuntime().addShutdownHook(this);
            synchronized (this) {
                if (!down) {
                    container = ServiceContainer.Factory.create("host-controller", false);
                    return container;
                } else {
                    throw new IllegalStateException();
                }
            }
        }

        private synchronized void setControlledProcessState(final ControlledProcessState ps) {
            this.processState = ps;
        }

        @Override
        public void run() {
            final ServiceContainer sc;
            final ControlledProcessState ps;
            synchronized (this) {
                down = true;
                sc = container;
                ps = processState;
            }
            try {
                if (ps != null) {
                    ps.setStopping();
                }
            } finally {
                if (sc != null) {
                    final CountDownLatch latch = new CountDownLatch(1);
                    sc.addTerminateListener(new ServiceContainer.TerminateListener() {
                        @Override
                        public void handleTermination(Info info) {
                            latch.countDown();
                        }
                    });
                    sc.shutdown();
                    // wait for all services to finish.
                    for (;;) {
                        try {
                            latch.await();
                            break;
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        }
    }

}
