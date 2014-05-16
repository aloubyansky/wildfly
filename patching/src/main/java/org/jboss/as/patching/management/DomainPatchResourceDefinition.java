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

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.as.patching.Constants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Alexey Loubyansky
 */
public class DomainPatchResourceDefinition extends SimpleResourceDefinition {

    public static final String NAME = "patching";
    static final String RESOURCE_NAME = DomainPatchResourceDefinition.class.getPackage().getName() + ".LocalDescriptions";
    static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, NAME);

    static final DomainPatchResourceDefinition INSTANCE = new DomainPatchResourceDefinition();

    static final AttributeDefinition INPUT_STREAM_IDX_DEF = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelType.INT)
            .setDefaultValue(new ModelNode(0))
            .setAllowNull(true)
            .build();

    static final OperationDefinition PATCH = new SimpleOperationDefinitionBuilder(Constants.PATCH, getResourceDescriptionResolver(DomainPatchResourceDefinition.NAME))
            .addParameter(INPUT_STREAM_IDX_DEF)
            .withFlag(Flag.MASTER_HOST_CONTROLLER_ONLY)
            .build();

    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix) {
        return new StandardResourceDescriptionResolver(keyPrefix, RESOURCE_NAME, PatchResourceDefinition.class.getClassLoader(), true, false);
    }

    private final List<AccessConstraintDefinition> sensitivity;

    private DomainPatchResourceDefinition() {
        super(PATH, getResourceDescriptionResolver(NAME));
        sensitivity = SensitiveTargetAccessConstraintDefinition.PATCHING.wrapAsList();
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registry) {
        super.registerOperations(registry);

        registry.registerOperationHandler(PATCH, DomainPatchStepHandler.INSTANCE);
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return sensitivity;
    }
}
