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

package org.jetbrains.jet.plugin.quickfix;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.diagnostics.Diagnostic;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingContextUtils;
import org.jetbrains.jet.lexer.JetKeywordToken;
import org.jetbrains.jet.lexer.JetToken;
import org.jetbrains.jet.lexer.JetTokens;
import org.jetbrains.jet.plugin.JetBundle;
import org.jetbrains.jet.plugin.project.AnalyzerFacadeWithCache;

public class ChangeVisibilityModifierOfDeclarationFix extends JetIntentionAction<PsiElement> {
    private JetKeywordToken modifier;
    private String str;

    public ChangeVisibilityModifierOfDeclarationFix(@NotNull PsiElement element, @NotNull JetKeywordToken modifier, @NotNull String str) {
        super(element);
        this.modifier = modifier;
        this.str = str;
    }

    @NotNull
    @Override
    public String getText() {
        return JetBundle.message("change.visibility.modifier") + " to " + str;
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return JetBundle.message("change.visibility.modifier");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof JetFile)) return false;
        return super.isAvailable(project, editor, file);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof JetFile)) return;

        JetReferenceExpression referenceExpression = PsiTreeUtil.getChildOfType(element, JetReferenceExpression.class);
        if (referenceExpression != null) {
            BindingContext bindingContext = AnalyzerFacadeWithCache.analyzeFileWithCache((JetFile) file).getBindingContext();
            PsiElement declaration = BindingContextUtils.resolveToDeclarationPsiElement(bindingContext, referenceExpression);
            JetToken[] modifiersThanCanBeReplaced = new JetKeywordToken[] { JetTokens.PUBLIC_KEYWORD, JetTokens.PRIVATE_KEYWORD, JetTokens.PROTECTED_KEYWORD, JetTokens.INTERNAL_KEYWORD };
            declaration.replace(AddModifierFix.addModifier(declaration, modifier, modifiersThanCanBeReplaced, project, true));
        }
    }

    public static JetIntentionActionFactory createFactory(final JetKeywordToken modifier, final String str) {

        return new JetIntentionActionFactory() {
            @Override
            public JetIntentionAction<PsiElement> createAction(Diagnostic diagnostic) {
                PsiElement element = diagnostic.getPsiElement();
                return new ChangeVisibilityModifierOfDeclarationFix(element, modifier, str);
            }
        };
    }

}
