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

package org.jboss.as.patching.management;

import static org.jboss.as.patching.management.PatchManagementMessages.MESSAGES;

import java.io.InputStream;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstallationManagerService;
import org.jboss.as.patching.runner.DomainPatchCoordinator;
import org.jboss.as.patching.tool.ContentVerificationPolicy;
import org.jboss.as.patching.tool.PatchTool;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceRegistry;

/**
 * @author Alexey Loubyansky
 *
 */
public class DomainPatchStepHandler implements OperationStepHandler {

    public static final OperationStepHandler INSTANCE = new DomainPatchStepHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ServiceRegistry registry = context.getServiceRegistry(true);
        final InstallationManager installationManager = (InstallationManager) registry.getRequiredService(InstallationManagerService.NAME).getValue();

        if (installationManager.requiresRestart()) {
            throw MESSAGES.serverRequiresRestart();
        }

        final ContentVerificationPolicy policy = PatchTool.Factory.create(operation);

        final int index = operation.get(ModelDescriptionConstants.INPUT_STREAM_INDEX).asInt(0);
        final InputStream is = context.getAttachmentStream(index);
        DomainPatchCoordinator coordinator = new DomainPatchCoordinator(installationManager);
        try {
            coordinator.apply(is, policy);
        } catch (PatchingException e) {
            throw new OperationFailedException("Failed to apply patch", e);
        }

        context.getResult().set(new ModelNode("ok"));
        context.stepCompleted();
    }
}
