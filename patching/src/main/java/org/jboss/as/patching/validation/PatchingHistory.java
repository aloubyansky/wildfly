/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.validation;


/**
 * @author Alexey Loubyansky
 *
 */
public class PatchingHistory extends AbstractArtifact<PatchingHistory.State,PatchingHistory.State> {

    private static final PatchingHistory INSTANCE = new PatchingHistory();

    public static PatchingHistory getInstance() {
        return INSTANCE;
    }

    private PatchingHistory() {
        addArtifact(PatchArtifact.getInstance());
    }

    public static class State implements Artifact.State {

        private PatchArtifact.CollectionState patches;

        public void setPatches(PatchArtifact.CollectionState patches) {
            this.patches = patches;
        }

        public PatchArtifact.CollectionState getPatches() {
            return patches;
        }

        public PatchArtifact.State getLastAppliedPatch() {
            patches.resetIndex();
            return patches.getState();
        }

        @Override
        public void validate(Context ctx) {
        }
    }

    @Override
    protected State getInitialState(State parent, Context ctx) {
        return new State();
    }

    public PatchArtifact.State getLastAppliedPatch(Context ctx) {
        return PatchArtifact.getInstance().getState(ctx).getState();
    }
}
