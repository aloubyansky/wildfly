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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.process.stdin.Base64InputStream;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.Unmarshaller;

/**
 * @author avoka
 *
 */
public class InputHandler {

    private final InputStream in;
    private int i;
    private boolean doI;
    private boolean closed;

    InputHandler(InputStream in) {
        if(in == null) {
            throw new IllegalArgumentException("Input stream is null");
        }
        this.in = in;
    }

    public boolean waitUntilAvailable() throws IOException {
        if(closed) {
            return false;
        }
        if(doI) {
            return true;
        }
        i = in.read();
        doI = true;
        if(i == -1) {
            closed = in.available() <= 0;
            return !closed;
        }
        return true;
    }

    public byte[] readBytes() throws IOException {
        if(closed) {
            throw new IOException("Input stream is closed");
        }
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final byte[] bytes = new byte[64];
        if(doI) {
            os.write(i);
            doI = false;
        }
        if(in.available() > 0) {
            int readTotal = in.read(bytes);
            while(readTotal > 0) {
                os.write(bytes, 0, readTotal);
                if(readTotal < bytes.length) {
                    break;
                }
                readTotal = in.read(bytes);
            }
            if(readTotal == -1) {
                closed = true;
            }
        }
        return os.toByteArray();
    }

    public InputStream getNextInput() throws IOException {
        final byte[] bytes = readBytes();
        return new ByteArrayInputStream(bytes);
    }

    public String unmarshalNext() throws IOException {
        final InputStream msgIs = getNextInput();
        final Base64InputStream is = new Base64InputStream(msgIs);
        Unmarshaller unmarshaller = null;
        try {
            unmarshaller = InputOutputHandlerFactory.getUnmarshaller();
            final ByteInput byteInput = Marshalling.createByteInput(is);
            unmarshaller.start(byteInput);
            return unmarshaller.readObject(String.class);
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed unmarshal object", e);
        } finally {
            if(unmarshaller != null) {
                unmarshaller.finish();
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
