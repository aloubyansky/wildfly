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
package org.jboss.as.patching.runner;

import static org.jboss.as.patching.runner.PatchingTaskContext.Mode.APPLY;
import static org.jboss.as.patching.runner.PatchingTaskContext.Mode.ROLLBACK;
import static org.jboss.as.patching.runner.PatchingTasks.ContentTaskDefinition;
import static org.jboss.as.patching.runner.PatchingTasks.apply;
import static org.jboss.as.patching.validation.PatchHistoryValidations.validateRollbackState;

import javax.xml.stream.XMLStreamException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.PatchLogger;
import org.jboss.as.patching.PatchMessages;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.InstalledImage;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.Identity;
import org.jboss.as.patching.metadata.LayerType;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.metadata.PatchElementProvider;
import org.jboss.as.patching.metadata.PatchMetadataResolver;
import org.jboss.as.patching.metadata.PatchXml;
import org.jboss.as.patching.metadata.RollbackPatch;
import org.jboss.as.patching.metadata.UpgradeCondition;
import org.jboss.as.patching.tool.ContentVerificationPolicy;
import org.jboss.as.patching.tool.PatchingHistory;
import org.jboss.as.patching.tool.PatchingResult;

/**
 * @author Emanuel Muckenhuber
 * @author Alexey Loubyansky
 */
class IdentityPhasedPatchRunner implements InstallationManager.ModificationCompletionCallback {

    private static final String DIRECTORY_SUFFIX = "jboss-as-patch-";
    private static final File TEMP_DIR = new File(SecurityActions.getSystemProperty("java.io.tmpdir"));

    private final InstalledImage installedImage;

    IdentityPhasedPatchRunner(InstalledImage installedImage) {
        this.installedImage = installedImage;
    }

    class State {

        Patch patch;
        PatchContentProvider contentProvider;
        ContentVerificationPolicy contentPolicy;
        InstallationManager.InstallationModification modification;
        private IdentityApplyCallback callback;
        private List<PreparedTask> tasks;
        private List<ContentItem> conflicts;
        private IdentityPatchContext context;

        void prepareTasksStep() throws PatchingException {

            // Check if we can apply this patch
            if (patch == null) {
                throw PatchMessages.MESSAGES.failedToResolvePatch(modification.getName(), modification.getVersion());
            }
            final String patchId = patch.getPatchId();
            final Identity identity = patch.getIdentity();
            final String appliesTo = identity.getVersion();
            if (!appliesTo.equals(modification.getVersion())) {
                throw PatchMessages.MESSAGES.doesNotApply(appliesTo, modification.getVersion());
            }
            // Cannot apply the same patch twice
            if (modification.isApplied(patchId)) {
                throw PatchMessages.MESSAGES.alreadyApplied(patchId);
            }

            // See if the prerequisites are met
            checkUpgradeConditions(patch.getIdentity(), modification);

            final File backup = installedImage.getPatchHistoryDir(patch.getPatchId());
            context = new IdentityPatchContext(backup, contentProvider, contentPolicy, modification, APPLY, installedImage);

            final Patch.PatchType patchType = identity.getPatchType();
            List<String> invalidation;
            if (patchType == Patch.PatchType.ONE_OFF) {
                // We don't need to invalidate anything
                invalidation = Collections.emptyList();
            } else {
                // Invalidate all installed patches (one-off, cumulative) - we never need to invalidate the release base
                invalidation = new ArrayList<String>(modification.getPatchIDs());
                if (!invalidation.isEmpty()) {
                    try {
                        // Before rolling back the one-off patches, validate that the state until that point is consistent
                        validateRollbackState(invalidation.get(invalidation.size() - 1),
                                modification.getUnmodifiedInstallationState());
                    } catch (PatchingException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new PatchingException(e);
                    }
                }
            }

            if (!invalidation.isEmpty()) {
                // Invalidate the installed patches first
                for (final String rollback : invalidation) {
                    rollback(rollback, context);
                }
            }

            // Add to installed patches list
            modification.addInstalledPatch(patchId);

            // Then apply the current patch
            for (final PatchElement element : patch.getElements()) {
                // Apply the content modifications
                final IdentityPatchContext.PatchEntry target = context.resolveForElement(element);
                final PatchElementProvider provider = element.getProvider();
                final Patch.PatchType elementPatchType = provider.getPatchType();
                final String elementPatchId = element.getId();
                // See if we can skip this element
                if (target.isApplied(elementPatchId)) {
                    // TODO if it is already applied, we can just skip the entry (maybe based ont the type of the patch)
                    // This needs some further testing, maybe we need to compare our history with the patch if they are consistent
                    throw PatchMessages.MESSAGES.alreadyApplied(elementPatchId);
                }
                // Check upgrade conditions
                checkUpgradeConditions(provider, target);
                apply(elementPatchId, element.getModifications(), target.getDefinitions());
                target.apply(elementPatchId, elementPatchType);
            }
            // Apply the patch to the identity
            final IdentityPatchContext.PatchEntry identityEntry = context.getIdentityEntry();
            apply(patchId, patch.getModifications(), identityEntry.getDefinitions());
            identityEntry.apply(patchId, patchType);

            // Port forward missing module changes
            if (patchType == Patch.PatchType.CUMULATIVE) {
                try {
                    portForward(patch, context);
                } catch (Exception e) {
                    PatchLogger.ROOT_LOGGER.debugf(e, "failed to apply patch %s", patchId);
                    throw rethrowException(e);
                }
            }

            // We need the resulting version for rollback
            if (patchType == Patch.PatchType.CUMULATIVE) {
                final Identity.IdentityUpgrade upgrade = identity.forType(Patch.PatchType.CUMULATIVE, Identity.IdentityUpgrade.class);
                identityEntry.setResultingVersion(upgrade.getResultingVersion());
            }

            callback = new IdentityApplyCallback(patch, identityEntry.getDirectoryStructure());
            tasks = new ArrayList<PreparedTask>();
            conflicts = new ArrayList<ContentItem>();

            // Identity
            prepareTasks(context.getIdentityEntry(), context, tasks, conflicts);
            // Layers
            for (final IdentityPatchContext.PatchEntry layer : context.getLayers()) {
                prepareTasks(layer, context, tasks, conflicts);
            }
            // AddOns
            for (final IdentityPatchContext.PatchEntry addOn : context.getAddOns()) {
                prepareTasks(addOn, context, tasks, conflicts);
            }
            // If there were problems report them
            if (!conflicts.isEmpty()) {
                throw PatchMessages.MESSAGES.conflictsDetected(conflicts);
            }
        }

        PatchingResult executeAndFinalizeStep() throws PatchingException {
            // Execute the tasks
            for (final PreparedTask task : tasks) {
                // Unless it's excluded by the user
                final ContentItem item = task.getContentItem();
                if (item != null && context.isExcluded(item)) {
                    continue;
                }
                // Run the task
                try {
                    task.execute();
                } catch (IOException e) {
                    PatchLogger.ROOT_LOGGER.debugf(e, "failed to apply patch %s", patch.getPatchId());
                    throw rethrowException(e);
                }
            }
            try {
                return context.finalize(callback);
            } catch (Exception e) {
                PatchLogger.ROOT_LOGGER.debugf(e, "failed to apply patch %s", patch.getPatchId());
                throw rethrowException(e);
            }
        }

        void cleanup() {
            contentProvider.cleanup();
        }
    }

    private State state;

    public void prepareToExecute(final PatchMetadataResolver patchResolver, final PatchContentProvider contentProvider, final ContentVerificationPolicy contentPolicy, final InstallationManager.InstallationModification modification) throws PatchingException {
        state = new State();
        state.contentPolicy = contentPolicy;
        state.contentProvider = contentProvider;
        state.patch = patchResolver.resolvePatch(modification.getName(), modification.getVersion());
        state.modification = modification;
        try {
            state.prepareTasksStep();
        } catch(Throwable e) {
            state.cleanup();
            state = null;
            throw rethrowException(e);
        }
    }

    public PatchingResult executeAndFinalize() throws PatchingException {
        try {
            return state.executeAndFinalizeStep();
        } finally {
            if(state != null) {
                state.cleanup();
                state = null;
            }
        }
    }

    @Override
    public void completed() {
        // nothing here
    }

    @Override
    public void canceled() {
        // nothing here
    }

    /**
     * Rollback a patch.
     *
     * @param patchID the patch id
     * @param context the patch context
     * @throws PatchingException
     */
    private void rollback(final String patchID, final IdentityPatchContext context) throws PatchingException {
        try {

            // Load the patch history
            final PatchingTaskContext.Mode mode = context.getMode();
            final Patch originalPatch = loadPatchInformation(patchID, installedImage);
            final RollbackPatch rollbackPatch = loadRollbackInformation(patchID, installedImage);
            final Patch.PatchType patchType = rollbackPatch.getIdentity().getPatchType();
            final InstalledIdentity history = rollbackPatch.getIdentityState();

            // Process originals by type first
            final LinkedHashMap<String, PatchElement> originalLayers = new LinkedHashMap<String, PatchElement>();
            final LinkedHashMap<String, PatchElement> originalAddOns = new LinkedHashMap<String, PatchElement>();
            for (final PatchElement patchElement : originalPatch.getElements()) {
                final PatchElementProvider provider = patchElement.getProvider();
                final String layerName = provider.getName();
                final LayerType layerType = provider.getLayerType();
                final Map<String, PatchElement> originals;
                switch (layerType) {
                    case Layer:
                        originals = originalLayers;
                        break;
                    case AddOn:
                        originals = originalAddOns;
                        break;
                    default:
                        throw new IllegalStateException();
                }
                if (!originals.containsKey(layerName)) {
                    originals.put(layerName, patchElement);
                } else {
                    throw PatchMessages.MESSAGES.installationDuplicateLayer(layerType.toString(), layerName);
                }
            }
            // Process the rollback xml
            for (final PatchElement patchElement : rollbackPatch.getElements()) {
                final String elementPatchId = patchElement.getId();
                final PatchElementProvider provider = patchElement.getProvider();
                final String layerName = provider.getName();
                final LayerType layerType = provider.getLayerType();
                final LinkedHashMap<String, PatchElement> originals;
                switch (layerType) {
                    case Layer:
                        originals = originalLayers;
                        break;
                    case AddOn:
                        originals = originalAddOns;
                        break;
                    default:
                        throw new IllegalStateException();
                }
                final PatchElement original = originals.remove(layerName);
                if (original == null) {
                    throw PatchMessages.MESSAGES.noSuchLayer(layerName);
                }
                final IdentityPatchContext.PatchEntry entry = context.resolveForElement(patchElement);
                final Map<Location, ContentTaskDefinition> modifications = entry.getDefinitions();
                // Create the rollback
                PatchingTasks.rollback(elementPatchId, original.getModifications(), patchElement.getModifications(), modifications, ContentItemFilter.ALL_BUT_MISC, mode);
                entry.rollback(original.getId());

                // We need to restore the previous state
                final Patch.PatchType elementPatchType = provider.getPatchType();
                final PatchableTarget.TargetInfo info;
                if (layerType == LayerType.AddOn) {
                    info = history.getAddOn(layerName).loadTargetInfo();
                } else {
                    info = history.getLayer(layerName).loadTargetInfo();
                }
                if (mode == ROLLBACK) {
                    restoreFromHistory(entry, elementPatchId, elementPatchType, info);
                }
            }
            if (!originalLayers.isEmpty() || !originalAddOns.isEmpty()) {
                throw PatchMessages.MESSAGES.invalidRollbackInformation();
            }

            // Rollback the patch
            final IdentityPatchContext.PatchEntry identity = context.getIdentityEntry();
            PatchingTasks.rollback(patchID, originalPatch.getModifications(), rollbackPatch.getModifications(), identity.getDefinitions(), ContentItemFilter.MISC_ONLY, mode);
            identity.rollback(patchID);

            // Restore previous state
            if (mode == ROLLBACK) {
                final PatchableTarget.TargetInfo identityHistory = history.getIdentity().loadTargetInfo();
                restoreFromHistory(identity, rollbackPatch.getPatchId(), patchType, identityHistory);
            }

            if (patchType == Patch.PatchType.CUMULATIVE) {
                final Identity.IdentityUpgrade upgrade = rollbackPatch.getIdentity().forType(Patch.PatchType.CUMULATIVE, Identity.IdentityUpgrade.class);
                identity.setResultingVersion(upgrade.getResultingVersion());
            }
        } catch (Exception e) {
            throw rethrowException(e);
        }
    }

    /**
     * Restore the recorded state from the rollback xml.
     *
     * @param target          the patchable target
     * @param rollbackPatchId the rollback patch id
     * @param patchType       the the current patch type
     * @param history         the recorded history
     * @throws PatchingException
     */
    static void restoreFromHistory(final InstallationManager.MutablePatchingTarget target, final String rollbackPatchId,
                                   final Patch.PatchType patchType, final PatchableTarget.TargetInfo history) throws PatchingException {
        if (patchType == Patch.PatchType.CUMULATIVE) {
            assert history.getCumulativePatchID().equals(rollbackPatchId);
            target.apply(rollbackPatchId, patchType);
            // Restore one off state
            final List<String> oneOffs = new ArrayList<String>(history.getPatchIDs());
            Collections.reverse(oneOffs);
            for (final String oneOff : oneOffs) {
                target.apply(oneOff, Patch.PatchType.ONE_OFF);
            }
        }
        checkState(history, history); // Just check for tests, that rollback should restore the old state
    }

    static void checkState(final PatchableTarget.TargetInfo o, final PatchableTarget.TargetInfo n) {
        assert n.getPatchIDs().equals(o.getPatchIDs());
        assert n.getCumulativePatchID().equals(o.getCumulativePatchID());
    }

    /**
     * Port forward missing module changes for each layer.
     *
     * @param patch   the current patch
     * @param context the patch context
     * @throws PatchingException
     * @throws IOException
     * @throws XMLStreamException
     */
    void portForward(final Patch patch, IdentityPatchContext context) throws PatchingException, IOException, XMLStreamException {
        assert patch.getIdentity().getPatchType() == Patch.PatchType.CUMULATIVE;

        final PatchingHistory history = context.getHistory();
        for (final PatchElement element : patch.getElements()) {

            final PatchElementProvider provider = element.getProvider();
            final String name = provider.getName();
            final boolean addOn = provider.isAddOn();

            final IdentityPatchContext.PatchEntry target = context.resolveForElement(element);
            final String cumulativePatchID = target.getCumulativePatchID();
            if (Constants.BASE.equals(cumulativePatchID)) {
                continue;
            }

            boolean found = false;
            final PatchingHistory.Iterator iterator = history.iterator();
            while (iterator.hasNextCP()) {
                final PatchingHistory.Entry entry = iterator.nextCP();
                final String patchId = addOn ? entry.getAddOnPatches().get(name) : entry.getLayerPatches().get(name);

                if (patchId != null && patchId.equals(cumulativePatchID)) {
                    final Patch original = loadPatchInformation(entry.getPatchId(), installedImage);
                    for (final PatchElement originalElement : original.getElements()) {
                        if (name.equals(originalElement.getProvider().getName())
                                && addOn == originalElement.getProvider().isAddOn()) {
                            PatchingTasks.addMissingModifications(cumulativePatchID, originalElement.getModifications(), target.getDefinitions(), ContentItemFilter.ALL_BUT_MISC);
                        }
                    }
                    // Record a loader to have access to the current modules
                    final DirectoryStructure structure = target.getDirectoryStructure();
                    final File modulesRoot = structure.getModulePatchDirectory(patchId);
                    final File bundlesRoot = structure.getBundlesPatchDirectory(patchId);
                    final PatchContentLoader loader = PatchContentLoader.create(null, bundlesRoot, modulesRoot);
                    context.recordContentLoader(patchId, loader);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw PatchMessages.MESSAGES.patchNotFoundInHistory(cumulativePatchID);
            }
        }
    }

    /**
     * Prepare all tasks.
     *
     * @param entry     the patch entry
     * @param context   the patch context
     * @param tasks     a list for prepared tasks
     * @param conflicts a list for conflicting content items
     * @throws PatchingException
     */
    static void prepareTasks(final IdentityPatchContext.PatchEntry entry, final IdentityPatchContext context, final List<PreparedTask> tasks, final List<ContentItem> conflicts) throws PatchingException {
        for (final PatchingTasks.ContentTaskDefinition definition : entry.getDefinitions().values()) {
            final PatchingTask task = createTask(definition, context, entry);
            try {
                // backup and validate content
                if (!task.prepare(entry) || definition.hasConflicts()) {
                    // Unless it a content item was manually ignored (or excluded)
                    final ContentItem item = task.getContentItem();
                    if (!context.isIgnored(item)) {
                        conflicts.add(item);
                    }
                }
                tasks.add(new PreparedTask(task, entry));
            } catch (IOException e) {
                throw new PatchingException(e);
            }
        }
    }

    /**
     * Create the patching task based on the definition.
     *
     * @param definition the task description
     * @param provider   the content provider
     * @param context    the task context
     * @return the created task
     */
    static PatchingTask createTask(final PatchingTasks.ContentTaskDefinition definition, final PatchContentProvider provider, final PatchingTaskContext context) {
        final PatchContentLoader contentLoader = provider.getLoader(definition.getTarget().getPatchId());
        final PatchingTaskDescription description = PatchingTaskDescription.create(definition, contentLoader);
        return PatchingTask.Factory.create(description, context);
    }

    static class PreparedTask {

        private final PatchingTask task;
        private final IdentityPatchContext.PatchEntry entry;

        PreparedTask(PatchingTask task, IdentityPatchContext.PatchEntry entry) {
            this.task = task;
            this.entry = entry;
        }

        ContentItem getContentItem() {
            return task.getContentItem();
        }

        protected void execute() throws IOException {
            task.execute(entry);
        }

    }

    static Patch loadPatchInformation(final String patchId, final InstalledImage installedImage) throws PatchingException, IOException, XMLStreamException {
        final File patchDir = installedImage.getPatchHistoryDir(patchId);
        final File patchXml = new File(patchDir, PatchXml.PATCH_XML);
        return PatchXml.parse(patchXml).resolvePatch(null, null);
    }

    static RollbackPatch loadRollbackInformation(final String patchId, final InstalledImage installedImage) throws PatchingException, IOException, XMLStreamException {
        final File historyDir = installedImage.getPatchHistoryDir(patchId);
        final File patchXml = new File(historyDir, Constants.ROLLBACK_XML);
        return (RollbackPatch) PatchXml.parse(patchXml).resolvePatch(null, null);
    }

    static File createTempDir() throws PatchingException {
        return createTempDir(TEMP_DIR);
    }

    static File createTempDir(final File parent) throws PatchingException {
        File workDir = null;
        int count = 0;
        while (workDir == null || workDir.exists()) {
            count++;
            workDir = new File(parent == null ? TEMP_DIR : parent, DIRECTORY_SUFFIX + count);
        }
        if (!workDir.mkdirs()) {
            throw new PatchingException(PatchMessages.MESSAGES.cannotCreateDirectory(workDir.getAbsolutePath()));
        }
        return workDir;
    }

    static PatchingException rethrowException(final Throwable e) {
        if (e instanceof PatchingException) {
            return (PatchingException) e;
        } else {
            return new PatchingException(e);
        }
    }

    /**
     * Check whether the patch can be applied to a given target.
     *
     * @param condition the conditions
     * @param target    the target
     * @throws PatchingException
     */
    static void checkUpgradeConditions(final UpgradeCondition condition, final InstallationManager.MutablePatchingTarget target) throws PatchingException {
        // See if the prerequisites are met
        for (final String required : condition.getRequires()) {
            if (!target.isApplied(required)) {
                throw PatchMessages.MESSAGES.requiresPatch(required);
            }
        }
        // Check for incompatibilities
        for (final String incompatible : condition.getIncompatibleWith()) {
            if (target.isApplied(incompatible)) {
                throw PatchMessages.MESSAGES.incompatiblePatch(incompatible);
            }
        }
    }

    static void assertExists(final File file) throws PatchingException {
        if (!file.exists()) {
            throw new PatchingException(PatchMessages.MESSAGES.fileDoesNotExist(file.getAbsolutePath()));
        }
    }

}
