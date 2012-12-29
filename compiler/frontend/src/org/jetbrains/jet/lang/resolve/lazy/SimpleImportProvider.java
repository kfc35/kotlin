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

import com.google.common.collect.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetImportDirective;
import org.jetbrains.jet.lang.psi.JetPsiUtil;
import org.jetbrains.jet.lang.resolve.ImportPath;
import org.jetbrains.jet.lang.resolve.name.Name;

import java.util.Collection;
import java.util.List;

public class SimpleImportProvider implements ImportDirectivesProvider {
    private final Collection<JetImportDirective> importDirectives;

    private ListMultimap<Name, JetImportDirective> exactImportMapping = null;
    private List<JetImportDirective> allUnderImports = null;
    private boolean indexed;

    public SimpleImportProvider(Collection<JetImportDirective> importDirectives) {
        this.importDirectives = importDirectives;
    }

    @NotNull
    @Override
    public List<JetImportDirective> getExactImports(@NotNull Name name) {
        createIndex();
        return exactImportMapping.get(name);
    }

    @NotNull
    @Override
    public List<JetImportDirective> getAllUnderImports() {
        createIndex();
        return allUnderImports;
    }

    @NotNull
    @Override
    public Collection<JetImportDirective> getAllSingleImports() {
        createIndex();
        return exactImportMapping.values();
    }

    private void createIndex() {
        if (indexed) {
            return;
        }

        ImmutableListMultimap.Builder<Name, JetImportDirective> exactImportMappingBuilder = ImmutableListMultimap.builder();
        ImmutableList.Builder<JetImportDirective> allImportsBuilder = ImmutableList.builder();

        for (JetImportDirective anImport : importDirectives) {
            ImportPath path = JetPsiUtil.getImportPath(anImport);
            if (path == null) {
                continue;
            }

            if (path.isAllUnder()) {
                allImportsBuilder.add(anImport);
            }
            else {
                Name aliasName = path.getImportedName();
                assert aliasName != null;

                exactImportMappingBuilder.put(aliasName, anImport);
            }
        }

        allUnderImports = allImportsBuilder.build();
        exactImportMapping = exactImportMappingBuilder.build();
        
        indexed = true;
    }
}
