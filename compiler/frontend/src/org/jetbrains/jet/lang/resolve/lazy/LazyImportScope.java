/*
 * Copyright 2010-2012 JetBrains s.r.o.
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

import static org.jetbrains.jet.lang.resolve.QualifiedExpressionResolver.LookupMode.EVERYTHING;

public class LazyImportScope implements JetScope {
    private final ResolveSession resolveSession;
    private final NamespaceDescriptor packageDescriptor;
    private final ImportDirectivesProvider importProvider;
    private final JetScope rootScope;
    private final String debugName;

    private boolean areAllSingleProcessed = false;
    private boolean areAllUnderProcessed = false;
    private final Set<JetImportDirective> processedDirectives = Sets.newHashSet();

    private final WritableScope delegateExactImportScope;
    private final WritableScope delegateAllSingleImportScope;

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

        delegateExactImportScope = new WritableScopeImpl(
                JetScope.EMPTY, packageDescriptor, RedeclarationHandler.DO_NOTHING,
                "Inner scope for exact imports in " + toString());
        delegateExactImportScope.changeLockLevel(WritableScope.LockLevel.BOTH);

        delegateAllSingleImportScope = new WritableScopeImpl(
                JetScope.EMPTY, packageDescriptor, RedeclarationHandler.DO_NOTHING,
                "Inner scope for all-under imports in " + toString());
        delegateAllSingleImportScope.changeLockLevel(WritableScope.LockLevel.BOTH);

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

        processImportDirectives(delegateExactImportScope, importProvider.getAllSingleImports());

        areAllSingleProcessed = true;
    }

    private void processImports(Name name) {
        processAllUnderImports();

        if (areAllSingleProcessed) {
            return;
        }

        processImportDirectives(delegateExactImportScope, importProvider.getExactImports(name));
    }

    private void processAllUnderImports() {
        if (areAllUnderProcessed) {
            return;
        }

        processImportDirectives(delegateAllSingleImportScope, importProvider.getAllUnderImports());

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
                        EVERYTHING);

                processedDirectives.add(directive);
            }
        }
    }

    @Nullable
    @Override
    public ClassifierDescriptor getClassifier(@NotNull Name name) {
        processImports(name);

        ClassDescriptor descriptor = delegateExactImportScope.getObjectDescriptor(name);
        if (descriptor != null) {
            return descriptor;
        }

        return delegateAllSingleImportScope.getObjectDescriptor(name);
    }

    @Nullable
    @Override
    public ClassDescriptor getObjectDescriptor(@NotNull Name name) {
        processImports(name);

        ClassDescriptor descriptor = delegateExactImportScope.getObjectDescriptor(name);
        if (descriptor != null) {
            return descriptor;
        }

        return delegateAllSingleImportScope.getObjectDescriptor(name);
    }

    @NotNull
    @Override
    public Collection<ClassDescriptor> getObjectDescriptors() {
        processAllImports();

        return ImmutableList.<ClassDescriptor>builder()
                .addAll(delegateExactImportScope.getObjectDescriptors())
                .addAll(delegateAllSingleImportScope.getObjectDescriptors())
                .build();
    }

    @Nullable
    @Override
    public NamespaceDescriptor getNamespace(@NotNull Name name) {
        processImports(name);

        NamespaceDescriptor descriptor = delegateExactImportScope.getNamespace(name);
        if (descriptor != null) {
            return descriptor;
        }

        return delegateAllSingleImportScope.getNamespace(name);
    }

    @NotNull
    @Override
    public Collection<VariableDescriptor> getProperties(@NotNull Name name) {
        processImports(name);

        return ImmutableList.<VariableDescriptor>builder()
                .addAll(delegateExactImportScope.getProperties(name))
                .addAll(delegateAllSingleImportScope.getProperties(name))
                .build();
    }

    @Nullable
    @Override
    public VariableDescriptor getLocalVariable(@NotNull Name name) {
        // TODO: Can we import local variables?

        processImports(name);

        VariableDescriptor descriptor = delegateExactImportScope.getLocalVariable(name);
        if (descriptor != null) {
            return descriptor;
        }

        return delegateAllSingleImportScope.getLocalVariable(name);
    }

    @NotNull
    @Override
    public Collection<FunctionDescriptor> getFunctions(@NotNull Name name) {
        processImports(name);

        return ImmutableList.<FunctionDescriptor>builder()
                .addAll(delegateExactImportScope.getFunctions(name))
                .addAll(delegateAllSingleImportScope.getFunctions(name))
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
                .addAll(delegateExactImportScope.getAllDescriptors())
                .addAll(delegateAllSingleImportScope.getAllDescriptors())
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
