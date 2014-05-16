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

package org.jboss.as.patching.process;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.net.SocketFactory;

import org.jboss.as.process.Main;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.as.process.ProcessInfo;
import org.jboss.as.process.ProcessMessageHandler;
import org.jboss.as.process.protocol.ProtocolClient;
import org.jboss.as.process.stdin.Base64InputStream;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.ByteOutput;
import org.jboss.util.Base64;

/**
 *
 * @author Alexey Loubyansky
 */
public class PatchingProcess {

    private static final String HOST_CONTROLLER_PROCESS_NAME = Main.HOST_CONTROLLER_PROCESS_NAME;

/*    public static class PatchInformation implements Serializable {

        private final InetSocketAddress pcAddress;
        private final InetSocketAddress mgmtAddress;

        private byte[] authKey;
        private String[] command;
        private Map<String, String> env;
        private String workDir;

        public PatchInformation(final InetSocketAddress pcAddress, final InetSocketAddress mgmtAddress, final byte[] authKey, final String[] command,
                                final Map<String, String> env, final String workingDir) {
            this.pcAddress = pcAddress;
            this.mgmtAddress = mgmtAddress;
            this.authKey = authKey;
            this.command = command;
            this.env = env;
            this.workDir = workingDir;
        }

        public InetSocketAddress getProcessControllerAddress() {
            return pcAddress;
        }

        public InetSocketAddress getMgmtAddress() {
            return mgmtAddress;
        }
    }
*/

    public static void main(String[] args) throws Exception {

        System.out.println("PATCHING PROCESS starts");
        byte[] authKey = null;
        String pcAddress = null;
        int pcPort = -1;
        String[] hcCmd = null;
        String wrkDir = null;
        int i = 0;
        while(i < args.length) {
            final String arg = args[i++];
            System.out.println(arg);
            if(arg.startsWith("--pc-address=")) {
                pcAddress = arg.substring("--pc-address=".length());
            } else if(arg.startsWith("--pc-port=")) {
                pcPort = Integer.parseInt(arg.substring("--pc-port=".length()));
            } else if(arg.startsWith("--hc-cmd=")) {
                final String str = arg.substring("--hc-cmd=".length());
                hcCmd = str.split("&");
                System.out.println("PATCHING PROCESS HC CMD " + str);
                for(String c : hcCmd) {
                    System.out.println("--- " + c);
                }
            } else if(arg.startsWith("--wrk-dir=")) {
                wrkDir = arg.substring("--wrk-dir=".length());
            } else if(arg.startsWith("--auth-key=")) {
                final String value = arg.substring("--auth-key=".length());
                authKey = Base64.decode(value);
            }
        }

        // TODO privileged block
        //System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");

        final InputStream initialInput = System.in;
//        final PrintStream initialError = System.err;

        // Install JBoss Stdio to avoid any nasty crosstalk.
/*        StdioContext.install();
        final StdioContext context = StdioContext.create(
                new NullInputStream(),
                new LoggingOutputStream(org.jboss.logmanager.Logger.getLogger("stdout"), Level.INFO),
                new LoggingOutputStream(org.jboss.logmanager.Logger.getLogger("stderr"), Level.ERROR)
                );
        StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(context));
*/
        final InputHandler input = InputOutputHandlerFactory.getInputHandler(initialInput);
        if(!input.waitUntilAvailable()) {
            System.exit(-6666);
        }

        Base64InputStream is = new Base64InputStream(input.getNextInput());
        byte[] bytes = new byte[16];
        int totalRead = is.read(bytes);
        System.out.println("PATCHING PROCESS read total " + totalRead);
        for (int j = 0; j < totalRead; ++j) {
            System.out.print(" " + bytes[j]);
        }
        System.out.println("");

        try {
            final ProtocolClient.Configuration configuration = new ProtocolClient.Configuration();
            configuration.setSocketFactory(SocketFactory.getDefault());
            configuration.setReadExecutor(Executors.newCachedThreadPool());
            configuration.setServerAddress(new InetSocketAddress(pcAddress, pcPort));
            configuration.setThreadFactory(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r);
                }
            });

            final ProcessControllerClient client = ProcessControllerClient.connect(configuration, authKey, new ProcessMessageHandler(){

                @Override
                public void handleProcessAdded(ProcessControllerClient client, String processName) {
                    System.out.println("PatchingProcess.handleProcessAdded " + processName);
                }

                @Override
                public void handleProcessStarted(ProcessControllerClient client, String processName) {
                    System.out.println("PatchingProcess.handleProcessStarted " + processName);
                }

                @Override
                public void handleProcessStopped(ProcessControllerClient client, String processName, long uptimeMillis) {
                    System.out.println("PatchingProcess.handleProcessStopped ##################### " + processName);
                }

                @Override
                public void handleProcessRemoved(ProcessControllerClient client, String processName) {
                    System.out.println("PatchingProcess.handleProcessRemoved ##################### " + processName);
                }

                @Override
                public void handleConnectionShutdown(ProcessControllerClient client) {
                    System.out.println("PatchingProcess.handleConnectionShutdown");
                }

                @Override
                public void handleConnectionFailure(ProcessControllerClient client, IOException cause) {
                    System.out.println("PatchingProcess.handleConnectionFailure");
                }

                @Override
                public void handleConnectionFinished(ProcessControllerClient client) {
                    System.out.println("PatchingProcess.handleConnectionFinished");
                }

                @Override
                public void handleOperationFailed(ProcessControllerClient client, OperationType operation, String processName) {
                    System.out.println("PatchingProcess.handleOperationFailed" + processName);
                }

                @Override
                public void handleProcessInventory(ProcessControllerClient client, Map<String, ProcessInfo> inventory) {
                    // TODO Auto-generated method stub

                }});

            final PatchingMessenger msgr = new PatchingMessenger(client, "Host Controller");
            msgr.send("PatchingProcess sais hi");
            System.out.println("PATCHING PROCESS marshalled msg, waiting for input");
            int msgCnt = 0;
            while(input.waitUntilAvailable()) {
                final String msg = input.unmarshalNext();
                System.out.println("PATCHING PROCESS received: " + msg);
                ++msgCnt;
                switch(msgCnt) {
                    case 1:
                        msgr.send("PatchingProcess: how are you?");
                        break;
                    case 2:
                        msgr.send("PatchingProcess: what are you doing there?");
                        break;
                    case 3:
                        System.out.println("PATCHING PROCESS restarting HC");
                        client.stopProcess(HOST_CONTROLLER_PROCESS_NAME);
                        client.removeProcess(HOST_CONTROLLER_PROCESS_NAME);
                        client.addProcess(HOST_CONTROLLER_PROCESS_NAME, authKey, hcCmd, wrkDir, Collections.<String,String>emptyMap());
                        client.startProcess(HOST_CONTROLLER_PROCESS_NAME);
                        System.out.println("PATCHING PROCESS restarting HC done");
                        break;
                    default:
                        if(msg.equals("HC AGENT is up")) {
                            msgr.send("PatchingProcess: welcome back!");
//                        } else {
//                            msgr.send("PatchingProcess sais quit");
                        }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(-6666);
            throw new IllegalStateException(); // not reached
        } finally {
            //
            System.out.println("PATCHING PROCESS DONE >>>>>>>>>>>>>>>>>>>>");
        }
        System.exit(-6666);
    }

    public static class DelegatingByteInput implements ByteInput {

        private final ByteInput input;

        public DelegatingByteInput(ByteInput input) {
            this.input = input;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        @Override
        public int read() throws IOException {
            final int result = input.read();
            //System.out.println("BYTE INPUT READ byte: " + (byte)result);
            return result;
        }

        @Override
        public int read(byte[] b) throws IOException {
            final int result = input.read(b);
/*            System.out.println("BYTE INPUT READ byte[]: " + result + " from " + b.length);
            if(result > 0) {
                for(int i = 0; i < result; ++i) {
                    System.out.print(" " + (byte)b[i]);
                }
                System.out.println("");
            }
            new Exception().printStackTrace();
*/            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            final int result = input.read(b, off, len);
/*            System.out.println("BYTE INPUT READ byte[] off len: " + result + " from " + len);
            if(result > 0) {
                for(int i = 0; i < result; ++i) {
                    System.out.print(" " + (byte)b[off+i]);
                }
                System.out.println("");
            }
*/            return result;
        }

        @Override
        public int available() throws IOException {
            return input.available();
        }

        @Override
        public long skip(long n) throws IOException {
            return input.skip(n);
        }

    }

    public static class DelegatingByteOutput implements ByteOutput {

        private final ByteOutput output;

        public DelegatingByteOutput(ByteOutput output) {
            this.output = output;
        }

        @Override
        public void close() throws IOException {
            output.close();
        }

        @Override
        public void flush() throws IOException {
            output.flush();
        }

        @Override
        public void write(int b) throws IOException {
            output.write(b);
//            System.out.println("BYTE OUTPUT WRITE byte: " + (byte)b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            output.write(b);
/*            System.out.println("BYTE OUTPUT WRITE byte[]: ");
            for(int i = 0; i < b.length; ++i) {
                System.out.print(" " + b[i]);
            }
            System.out.println("");
*/        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            output.write(b, off, len);
/*            System.out.println("BYTE OUTPUT WRITE byte[] off len: ");
            for(int i = 0; i < len; ++i) {
                System.out.print(" " + b[off + i]);
            }
            System.out.println("");
            new Exception().printStackTrace();
*/        }

    }
}
