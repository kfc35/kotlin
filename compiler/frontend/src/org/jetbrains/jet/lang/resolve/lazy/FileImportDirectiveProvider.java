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

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.psi.JetImportDirective;
import org.jetbrains.jet.lang.psi.JetPsiBuilder;
import org.jetbrains.jet.lang.resolve.ImportPath;
import org.jetbrains.jet.lang.resolve.name.Name;

import java.util.Collection;
import java.util.List;

public class FileImportDirectiveProvider implements ImportDirectivesProvider {
    private final JetFile file;
    private final List<ImportPath> defaultImports;
    private final JetPsiBuilder psiBuilder;

    private boolean indexed;
    private SimpleImportProvider directiveProvider = null;

    FileImportDirectiveProvider(JetFile file, List<ImportPath> defaultImports, JetPsiBuilder psiBuilder) {
        this.file = file;
        this.defaultImports = defaultImports;
        this.psiBuilder = psiBuilder;
    }

    @NotNull
    @Override
    public List<JetImportDirective> getImportDirectives(@NotNull Name name) {
        createIndex();
        return directiveProvider.getImportDirectives(name);
    }

    @NotNull
    @Override
    public List<JetImportDirective> getAllImports() {
        createIndex();
        return directiveProvider.getAllImports();
    }

    private void createIndex() {
        if (indexed) {
            return;
        }

        directiveProvider = new SimpleImportProvider(collectImports());

        indexed = true;
    }

    private Collection<JetImportDirective> collectImports() {
        Collection<JetImportDirective> allImports = Lists.newArrayList();

        for (ImportPath defaultImport : defaultImports) {
            allImports.add(psiBuilder.createImportDirective(defaultImport));
        }

        allImports.addAll(file.getImportDirectives());

        return allImports;
    }
}
