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
import java.io.OutputStream;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;

/**
 * @author avoka
 *
 */
public class InputOutputHandlerFactory {

    private static final MarshallerFactory MARSHALLER_FACTORY;
    private static final MarshallingConfiguration CONFIG;
    static {
        try {
            MARSHALLER_FACTORY = Marshalling.getMarshallerFactory("river",
                    Module.getModuleFromCallerModuleLoader(ModuleIdentifier.fromString("org.jboss.marshalling.river"))
                            .getClassLoader());
        } catch (ModuleLoadException e) {
            throw new RuntimeException(e);
        }
        final ClassLoader cl = InputHandler.class.getClassLoader();
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setVersion(2);
        config.setClassResolver(new SimpleClassResolver(cl));
        CONFIG = config;
    }

    public static Unmarshaller getUnmarshaller() throws IOException {
        return MARSHALLER_FACTORY.createUnmarshaller(CONFIG);
    }

    public static Marshaller getMarshaller() throws IOException {
        return MARSHALLER_FACTORY.createMarshaller(CONFIG);
    }

    public static InputHandler getInputHandler(InputStream is) {
        return new InputHandler(is);
    }

    public static OutputHandler getOutputHandler(OutputStream os) {
        return new OutputHandler(os);
    }
}
