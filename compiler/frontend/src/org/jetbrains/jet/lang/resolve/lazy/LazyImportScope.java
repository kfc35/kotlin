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

import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.JetImportDirective;
import org.jetbrains.jet.lang.resolve.Importer;
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

public class LazyImportScope implements JetScope {
    @NotNull private final ResolveSession resolveSession;
    @NotNull private final NamespaceDescriptor packageDescriptor;
    @NotNull private final ImportDirectivesProvider importProvider;
    @NotNull private final String debugName;

    @NotNull private final Set<Pair<JetImportDirective, Boolean>> processedImports = Sets.newHashSet();

    private final WritableScopeImpl resolveImportScope;
    private final WritableScopeImpl delegateImportScope;

    public LazyImportScope(
            @NotNull ResolveSession resolveSession,
            @NotNull NamespaceDescriptor packageDescriptor,
            @NotNull NamespaceDescriptor rootPackageDescriptor,
            @NotNull ImportDirectivesProvider importProvider,
            @NotNull String debugName
    ) {
        this.resolveSession = resolveSession;
        this.packageDescriptor = packageDescriptor;
        this.importProvider = importProvider;
        this.debugName = debugName;

        // Scope for storing all processed imports
        delegateImportScope = new WritableScopeImpl(
                JetScope.EMPTY, packageDescriptor, RedeclarationHandler.DO_NOTHING,
                "Inner scope in " + toString());
        delegateImportScope.changeLockLevel(WritableScope.LockLevel.BOTH);

        // Create special scope for resolving imports
        resolveImportScope = new WritableScopeImpl(
                JetScope.EMPTY, packageDescriptor, RedeclarationHandler.DO_NOTHING,
                "Temp scope for import resolve in " + toString());
        resolveImportScope.importScope(rootPackageDescriptor.getMemberScope());
        resolveImportScope.changeLockLevel(WritableScope.LockLevel.READING);
    }

    @Nullable
    @Override
    public ClassifierDescriptor getClassifier(@NotNull Name name) {
        processImports(importProvider.getImportDirectives(name), true);
        return delegateImportScope.getClassifier(name);
    }

    private void processImports(Collection<JetImportDirective> directives, final boolean onlyClasses) {
        Importer.StandardImporter importer = new Importer.StandardImporter(delegateImportScope);

        for (JetImportDirective unprocessedImport : directives) {
            Pair<JetImportDirective, Boolean> processedKey = Pair.create(unprocessedImport, onlyClasses);
            if (!processedImports.contains(processedKey)) {
                resolveSession.getInjector().getQualifiedExpressionResolver().processImportReference(
                        unprocessedImport,
                        resolveImportScope,
                        packageDescriptor.getMemberScope(),
                        importer,
                        resolveSession.getTrace(),
                        resolveSession.getModuleConfiguration(), onlyClasses);

                processedImports.add(processedKey);
            }
        }
    }

    @Nullable
    @Override
    public ClassDescriptor getObjectDescriptor(@NotNull Name name) {
        processImports(importProvider.getImportDirectives(name), true);
        return delegateImportScope.getObjectDescriptor(name);
    }

    @NotNull
    @Override
    public Collection<ClassDescriptor> getObjectDescriptors() {
        processImports(importProvider.getAllImports(), true);
        return delegateImportScope.getObjectDescriptors();
    }

    @Nullable
    @Override
    public NamespaceDescriptor getNamespace(@NotNull Name name) {
        processImports(importProvider.getImportDirectives(name), true);
        return delegateImportScope.getNamespace(name);
    }

    @NotNull
    @Override
    public Collection<VariableDescriptor> getProperties(@NotNull Name name) {
        processImports(importProvider.getImportDirectives(name), false);
        return delegateImportScope.getProperties(name);
    }

    @Nullable
    @Override
    public VariableDescriptor getLocalVariable(@NotNull Name name) {
        processImports(importProvider.getImportDirectives(name), false);
        return delegateImportScope.getLocalVariable(name);
    }

    @NotNull
    @Override
    public Collection<FunctionDescriptor> getFunctions(@NotNull Name name) {
        processImports(importProvider.getImportDirectives(name), false);
        return delegateImportScope.getFunctions(name);
    }

    @NotNull
    @Override
    public DeclarationDescriptor getContainingDeclaration() {
        return delegateImportScope.getContainingDeclaration();
    }

    @NotNull
    @Override
    public Collection<DeclarationDescriptor> getDeclarationsByLabel(@NotNull LabelName labelName) {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public PropertyDescriptor getPropertyByFieldReference(@NotNull Name fieldName) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Collection<DeclarationDescriptor> getAllDescriptors() {
        processImports(importProvider.getAllImports(), true);
        return delegateImportScope.getAllDescriptors();
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
