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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetImportDirective;
import org.jetbrains.jet.lang.psi.JetPsiUtil;
import org.jetbrains.jet.lang.resolve.ImportPath;
import org.jetbrains.jet.lang.resolve.name.Name;

import java.util.Collection;
import java.util.List;

public class SimpleImportProvider implements ImportDirectivesProvider {
    private final Collection<JetImportDirective> importDirectives;

    private Multimap<Name, JetImportDirective> exactImportMapping = null;
    private List<JetImportDirective> allUnderImports = null;
    private boolean indexed;

    public SimpleImportProvider(Collection<JetImportDirective> importDirectives) {
        this.importDirectives = importDirectives;
    }

    @NotNull
    @Override
    public List<JetImportDirective> getImportDirectives(@NotNull Name name) {
        createIndex();

        assert allUnderImports != null && exactImportMapping != null;

        Collection<JetImportDirective> exactImports = exactImportMapping.get(name);
        if (exactImports.isEmpty()) {
            return allUnderImports;
        }

        return ImmutableList.<JetImportDirective>builder()
                .addAll(allUnderImports)
                .addAll(exactImports)
                .build();
    }

    @NotNull
    @Override
    public List<JetImportDirective> getAllImports() {
        createIndex();

        return ImmutableList.<JetImportDirective>builder()
                .addAll(allUnderImports)
                .addAll(exactImportMapping.values())
                .build();
    }

    private void createIndex() {
        if (indexed) {
            return;
        }

        ImmutableMultimap.Builder<Name, JetImportDirective> exactImportMappingBuilder = ImmutableSetMultimap.builder();
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
                Name aliasName = JetPsiUtil.getAliasName(anImport);
                if (aliasName != null) {
                    exactImportMappingBuilder.put(aliasName, anImport);
                }
            }
        }

        allUnderImports = allImportsBuilder.build();
        exactImportMapping = exactImportMappingBuilder.build();
        
        indexed = true;
    }
}
