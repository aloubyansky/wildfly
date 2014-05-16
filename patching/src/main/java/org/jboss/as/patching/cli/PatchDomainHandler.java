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

package org.jboss.as.patching.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;

import java.io.File;
import java.io.IOException;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.handlers.DefaultFilenameTabCompleter;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.handlers.WindowsFilenameTabCompleter;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * @author Alexey Loubyansky
 *
 */
public class PatchDomainHandler extends CommandHandlerWithHelp {

//    private static final String PATCH = "patch";
    static final String PATCH_DOMAIN = "patch-domain";

    private final ArgumentWithoutValue path;

    public PatchDomainHandler(CommandContext ctx) {
        super(PATCH_DOMAIN, true);

        final FilenameTabCompleter pathCompleter = Util.isWindows() ? new WindowsFilenameTabCompleter(ctx) : new DefaultFilenameTabCompleter(ctx);
        path = new FileSystemPathArgument(this, pathCompleter, 0, "--path");
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ParsedCommandLine args = ctx.getParsedCommandLine();
        final String path = this.path.getValue(args, true);

        final File f = new File(path);
        if(!f.exists()) {
            // i18n is never used for CLI exceptions
            throw new CommandFormatException("Path " + f.getAbsolutePath() + " doesn't exist.");
        }
        if(f.isDirectory()) {
            throw new CommandFormatException(f.getAbsolutePath() + " is a directory.");
        }

        final ModelNode address = new ModelNode();
        address.add("core-service", "patching");

        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set("patch");
        operation.get(ModelDescriptionConstants.OP_ADDR).set(address);

        operation.get(INPUT_STREAM_INDEX).set(0);
        final OperationBuilder operationBuilder = OperationBuilder.create(operation);
        operationBuilder.addFileAsAttachment(f);

        ModelNode response = null;
        try {
            response = ctx.getModelControllerClient().execute(operationBuilder.build());
        } catch (IOException e) {
            e.printStackTrace();
        }

        ctx.printLine("WORKING " + response);
    }
}
