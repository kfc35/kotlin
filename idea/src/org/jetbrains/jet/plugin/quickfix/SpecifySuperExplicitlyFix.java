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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.impl.MutableClassDescriptor;
import org.jetbrains.jet.lang.diagnostics.Diagnostic;
import org.jetbrains.jet.lang.psi.JetDotQualifiedExpression;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.psi.JetReferenceExpression;
import org.jetbrains.jet.lang.psi.JetSuperExpression;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.types.checker.JetTypeChecker;
import org.jetbrains.jet.plugin.JetBundle;
import org.jetbrains.jet.plugin.caches.resolve.KotlinCacheManager;
import org.jetbrains.jet.plugin.caches.resolve.KotlinCacheManagerUtil;
import org.jetbrains.jet.plugin.project.AnalyzerFacadeWithCache;
import org.jetbrains.jet.renderer.DescriptorRenderer;

import java.util.Collection;
import java.util.LinkedHashSet;

public class SpecifySuperExplicitlyFix extends JetIntentionAction<JetSuperExpression> {

    public SpecifySuperExplicitlyFix(@NotNull JetSuperExpression element) {
        super(element);
    }

    @NotNull
    @Override
    public String getText() {
        return "";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return JetBundle.message("map.platform.class.to.kotlin.family");
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        BindingContext contextClass = KotlinCacheManagerUtil.getDeclarationsFromProject(element).getBindingContext();
        BindingContext contextExpressions = AnalyzerFacadeWithCache.analyzeFileWithCache((JetFile) file).getBindingContext();


        //JetTypeChecker.INSTANCE.isSubtypeOf()

    }

    public static JetIntentionActionFactory createFactory() {
        return new JetIntentionActionFactory() {
            @Nullable
            @Override
            public IntentionAction createAction(Diagnostic diagnostic) {
                JetSuperExpression superExp = QuickFixUtil.getParentElementOfType(diagnostic, JetSuperExpression.class);
                if (superExp == null) {
                    return null;
                }
                //Get the superclasses?

                JetDotQualifiedExpression wholeExp = PsiTreeUtil.getParentOfType(superExp, JetDotQualifiedExpression.class);
                if (wholeExp == null) {
                    return null;
                }
                //TODO do square brackets
                return null;
            }
        };
    }

    /*Taken and modified from MapPlatformClassToKotlinFix*/
    //TODO change replacedElements to one element
    private static void buildAndShowTemplate(
            Project project, Editor editor, PsiFile file,
            PsiElement replacedElement, LinkedHashSet<String> options
    ) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
        final CaretModel caretModel = editor.getCaretModel();
        final int oldOffset = caretModel.getOffset();
        caretModel.moveToOffset(file.getNode().getStartOffset());

        TemplateBuilderImpl builder = new TemplateBuilderImpl(file);
        Expression expression = new MyLookupExpression(replacedElement.getText(), options, null, false,
                                                       JetBundle.message("specify.super.explicitly.advertisement"));

        builder.replaceElement(replacedElement, null, expression, true);
        TemplateManager.getInstance(project).startTemplate(editor, builder.buildInlineTemplate(), new TemplateEditingAdapter() {
            @Override
            public void templateFinished(Template template, boolean brokenOff) {
                caretModel.moveToOffset(oldOffset);
            }
        });
    }

    /*Taken and modified from MapPlatformClassToKotlinFix*/
    @Nullable
    private static MutableClassDescriptor resolveToClass(@NotNull JetReferenceExpression referenceExpression, @NotNull BindingContext context) {
        DeclarationDescriptor descriptor = context.get(BindingContext.REFERENCE_TARGET, referenceExpression);
        Collection<? extends DeclarationDescriptor> ambiguousTargets =
                context.get(BindingContext.AMBIGUOUS_REFERENCE_TARGET, referenceExpression);
        if (descriptor instanceof MutableClassDescriptor) {
            return (MutableClassDescriptor) descriptor;
        }
        else if (ambiguousTargets != null) {
            for (DeclarationDescriptor target : ambiguousTargets) {
                if (target instanceof MutableClassDescriptor) {
                    return (MutableClassDescriptor) target;
                }
            }
        }
        return null;
    }
}
