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

import org.jboss.as.controller.registry.AbstractModelResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * @author Alexey Loubyansky
 */
public class DomainPatchResource extends AbstractModelResource {

    /**
     * The local model.
     */
    private final ModelNode model = new ModelNode();

    public DomainPatchResource() {
    }

    @Override
    public ModelNode getModel() {
        return model;
    }

    @Override
    public void writeModel(ModelNode newModel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isModelDefined() {
        return model.isDefined();
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public Resource clone() {
        return this;
    }
}
