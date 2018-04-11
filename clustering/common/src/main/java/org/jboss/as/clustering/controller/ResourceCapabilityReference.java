/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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

package org.jboss.as.clustering.controller;

import java.util.function.Function;

import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.wildfly.clustering.service.BinaryRequirement;
import org.wildfly.clustering.service.Requirement;
import org.wildfly.clustering.service.UnaryRequirement;

/**
 * {@link CapabilityReferenceRecorder} for resource-level capability references.
 * @author Paul Ferraro
 */
public class ResourceCapabilityReference implements CapabilityReferenceRecorder {

    private final Capability capability;
    private final Requirement requirement;
    private final Function<PathAddress, String[]> requirementNameResolver;

    public ResourceCapabilityReference(Capability capability, UnaryRequirement requirement, Function<PathAddress, String[]> requirementNameResolver) {
        this.capability = capability;
        this.requirement = requirement;
        this.requirementNameResolver = requirementNameResolver;
    }

    public ResourceCapabilityReference(Capability capability, BinaryRequirement requirement, Function<PathAddress, String[]> requirementNameResolver) {
        this.capability = capability;
        this.requirement = requirement;
        this.requirementNameResolver = requirementNameResolver;
    }

    @Override
    public void addCapabilityRequirements(OperationContext context, Resource resource,  String attributeName, String... values) {
        assert values.length == 0;
        context.registerAdditionalCapabilityRequirement(this.getRequirementName(context), this.getDependentName(context), attributeName);
    }

    @Override
    public void removeCapabilityRequirements(OperationContext context, Resource resource, String attributeName, String... values) {
        assert values.length == 0;
        context.deregisterCapabilityRequirement(this.getRequirementName(context), this.getDependentName(context));
    }

    private String getDependentName(OperationContext context) {
        return this.capability.resolve(context.getCurrentAddress()).getName();
    }

    private String getRequirementName(OperationContext context) {
        return RuntimeCapability.buildDynamicCapabilityName(this.requirement.getName(), this.requirementNameResolver.apply(context.getCurrentAddress()));
    }

    @Override
    @Deprecated
    public String getBaseDependentName() {
        return this.capability.getName();
    }

    @Override
    public String getBaseRequirementName() {
        return this.requirement.getName();
    }

    @Override
    public String[] getRequirementPatternSegments(String name, PathAddress address) {
        assert name == null;
        return this.requirementNameResolver.apply(address);
    }
}
