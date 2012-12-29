/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.lang.resolve.lazy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.JetImportDirective;
import org.jetbrains.jet.lang.resolve.Importer;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.LabelName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.resolve.scopes.JetScope;
import org.jetbrains.jet.lang.resolve.scopes.RedeclarationHandler;
import org.jetbrains.jet.lang.resolve.scopes.WritableScope;
import org.jetbrains.jet.lang.resolve.scopes.WritableScopeImpl;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.jetbrains.jet.lang.resolve.QualifiedExpressionResolver.LookupMode;

public class LazyImportScope implements JetScope {
    private final ResolveSession resolveSession;
    private final NamespaceDescriptor packageDescriptor;
    private final ImportDirectivesProvider importProvider;
    private final JetScope rootScope;
    private final String debugName;

    private boolean areAllSingleProcessed = false;
    private boolean areAllUnderProcessed = false;
    private final Set<JetImportDirective> processedDirectives = Sets.newHashSet();

    private final WritableScope delegateSingleImportsScope;
    private final WritableScope delegateAllUnderImportsScope;

    public LazyImportScope(
            @NotNull ResolveSession resolveSession,
            @NotNull NamespaceDescriptor packageDescriptor,
            @NotNull ImportDirectivesProvider importProvider,
            @NotNull String debugName
    ) {
        this.resolveSession = resolveSession;
        this.packageDescriptor = packageDescriptor;
        this.importProvider = importProvider;
        this.debugName = debugName;

        delegateSingleImportsScope = new WritableScopeImpl(
                JetScope.EMPTY, packageDescriptor, RedeclarationHandler.DO_NOTHING,
                "Inner scope for exact imports in " + toString());
        delegateSingleImportsScope.changeLockLevel(WritableScope.LockLevel.BOTH);

        delegateAllUnderImportsScope = new WritableScopeImpl(
                JetScope.EMPTY, packageDescriptor, RedeclarationHandler.DO_NOTHING,
                "Inner scope for all-under imports in " + toString());
        delegateAllUnderImportsScope.changeLockLevel(WritableScope.LockLevel.BOTH);

        NamespaceDescriptor rootPackageDescriptor = resolveSession.getPackageDescriptorByFqName(FqName.ROOT);
        if (rootPackageDescriptor == null) {
            throw new IllegalStateException("Root package not found");
        }
        rootScope = rootPackageDescriptor.getMemberScope();
    }

    private void processAllImports() {
        processAllUnderImports();

        if (areAllSingleProcessed) {
            return;
        }

        processImportDirectives(delegateSingleImportsScope, importProvider.getAllSingleImports());

        areAllSingleProcessed = true;
    }

    private void processImports(Name name) {
        processAllUnderImports();

        if (areAllSingleProcessed) {
            return;
        }

        processImportDirectives(delegateSingleImportsScope, importProvider.getExactImports(name));
    }

    private void processAllUnderImports() {
        if (areAllUnderProcessed) {
            return;
        }

        processImportDirectives(delegateAllUnderImportsScope, importProvider.getAllUnderImports());

        areAllUnderProcessed = true;
    }

    private void processImportDirectives(
            WritableScope scopeForStorage,
            Collection<JetImportDirective> directives
    ) {
        if (directives.isEmpty()) {
            return;
        }

        Importer.StandardImporter importer = new Importer.StandardImporter(scopeForStorage);

        for (JetImportDirective directive : directives) {
            if (!processedDirectives.contains(directive)) {
                resolveSession.getInjector().getQualifiedExpressionResolver().processImportReference(
                        directive,
                        rootScope,
                        packageDescriptor.getMemberScope(),
                        importer,
                        resolveSession.getTrace(),
                        resolveSession.getModuleConfiguration(),
                        LookupMode.ONLY_CLASSES);

                processedDirectives.add(directive);
            }
        }
    }

    @Nullable
    @Override
    public ClassifierDescriptor getClassifier(@NotNull Name name) {
        processImports(name);

        ClassDescriptor descriptor = delegateSingleImportsScope.getObjectDescriptor(name);
        if (descriptor != null) {
            return descriptor;
        }

        return delegateAllUnderImportsScope.getClassifier(name);
    }

    @Nullable
    @Override
    public ClassDescriptor getObjectDescriptor(@NotNull Name name) {
        processImports(name);

        ClassDescriptor descriptor = delegateSingleImportsScope.getObjectDescriptor(name);
        if (descriptor != null) {
            return descriptor;
        }

        return delegateAllUnderImportsScope.getObjectDescriptor(name);
    }

    @NotNull
    @Override
    public Collection<ClassDescriptor> getObjectDescriptors() {
        processAllImports();

        return ImmutableList.<ClassDescriptor>builder()
                .addAll(delegateSingleImportsScope.getObjectDescriptors())
                .addAll(delegateAllUnderImportsScope.getObjectDescriptors())
                .build();
    }

    @Nullable
    @Override
    public NamespaceDescriptor getNamespace(@NotNull Name name) {
        processImports(name);

        NamespaceDescriptor descriptor = delegateSingleImportsScope.getNamespace(name);
        if (descriptor != null) {
            return descriptor;
        }

        return delegateAllUnderImportsScope.getNamespace(name);
    }

    @NotNull
    @Override
    public Collection<VariableDescriptor> getProperties(@NotNull Name name) {
        processImports(name);

        return ImmutableList.<VariableDescriptor>builder()
                .addAll(delegateSingleImportsScope.getProperties(name))
                .addAll(delegateAllUnderImportsScope.getProperties(name))
                .build();
    }

    @Nullable
    @Override
    public VariableDescriptor getLocalVariable(@NotNull Name name) {
        // TODO: Can we import local variables?

        processImports(name);

        VariableDescriptor descriptor = delegateSingleImportsScope.getLocalVariable(name);
        if (descriptor != null) {
            return descriptor;
        }

        return delegateAllUnderImportsScope.getLocalVariable(name);
    }

    @NotNull
    @Override
    public Collection<FunctionDescriptor> getFunctions(@NotNull Name name) {
        processImports(name);

        return ImmutableList.<FunctionDescriptor>builder()
                .addAll(delegateSingleImportsScope.getFunctions(name))
                .addAll(delegateAllUnderImportsScope.getFunctions(name))
                .build();
    }

    @NotNull
    @Override
    public DeclarationDescriptor getContainingDeclaration() {
        return packageDescriptor;
    }

    @NotNull
    @Override
    public Collection<DeclarationDescriptor> getDeclarationsByLabel(@NotNull LabelName labelName) {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public PropertyDescriptor getPropertyByFieldReference(@NotNull Name fieldName) {
        return null;
    }

    @NotNull
    @Override
    public Collection<DeclarationDescriptor> getAllDescriptors() {
        processAllImports();

        return ImmutableList.<DeclarationDescriptor>builder()
                .addAll(delegateSingleImportsScope.getAllDescriptors())
                .addAll(delegateAllUnderImportsScope.getAllDescriptors())
                .build();
    }

    @NotNull
    @Override
    public List<ReceiverParameterDescriptor> getImplicitReceiversHierarchy() {
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<DeclarationDescriptor> getOwnDeclaredDescriptors() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "LazyImportScope: " + debugName;
    }
}
