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
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.impl.MutableClassDescriptor;
import org.jetbrains.jet.lang.diagnostics.Diagnostic;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.checker.JetTypeChecker;
import org.jetbrains.jet.plugin.JetBundle;
import org.jetbrains.jet.plugin.caches.resolve.KotlinCacheManager;
import org.jetbrains.jet.plugin.caches.resolve.KotlinCacheManagerUtil;
import org.jetbrains.jet.plugin.project.AnalyzerFacadeWithCache;
import org.jetbrains.jet.plugin.references.JetSimpleNameReference;
import org.jetbrains.jet.renderer.DescriptorRenderer;

import java.util.Collection;
import java.util.LinkedHashSet;

public class SpecifySuperExplicitlyFix extends JetIntentionAction<JetSuperExpression> {
    private final LinkedHashSet<MutableClassDescriptor> superClassDescs;
    private final PsiElement elementToReplace;
    private final JetTypeReference expectedType;
    private final String memberName;
    private final JetValueArgumentList valArgs;

    public SpecifySuperExplicitlyFix(@NotNull JetSuperExpression element, @NotNull LinkedHashSet<MutableClassDescriptor> superClassDescs,
            @NotNull PsiElement elementToReplace, @Nullable JetTypeReference expectedType, @NotNull String memberName,
            @Nullable JetValueArgumentList valArgs) {
        super(element);
        this.superClassDescs = superClassDescs;
        this.elementToReplace = elementToReplace;
        this.expectedType = expectedType;
        this.memberName = memberName;
        this.valArgs = valArgs;
    }

    @NotNull
    @Override
    public String getText() {
        return JetBundle.message("specify.super.explicitly");
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return JetBundle.message("specify.super.explicitly.family");
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        //BindingContext contextClasses = KotlinCacheManagerUtil.getDeclarationsFromProject(element).getBindingContext();
        BindingContext contextExpressions = AnalyzerFacadeWithCache.analyzeFileWithCache((JetFile) file).getBindingContext();

        JetType expectedJetType = null;
        if (expectedType != null) {
            expectedJetType = contextExpressions.get(BindingContext.TYPE, expectedType);
            System.out.println("expected Jet Type = " + expectedJetType);
        }
        LinkedHashSet<String> options = new LinkedHashSet<String>();

        for (MutableClassDescriptor classDesc : superClassDescs) {
            for (CallableMemberDescriptor memberDesc : classDesc.getAllCallableMembers()) {
                if (memberDesc instanceof FunctionDescriptor) {
                    //TODO applying square brackets.
                    System.out.println(memberDesc + " is a function with return type " + memberDesc.getReturnType());
                    if (memberDesc.getName().getName().equals(memberName)) {
                        System.out.println("member name matches for this function!");
                        if (memberDesc.getModality() == Modality.ABSTRACT) {
                            continue;
                        }

                    }
                }
                else if (memberDesc instanceof PropertyDescriptor) {
                    System.out.println(memberDesc + " is a property with type " + ((PropertyDescriptor) memberDesc).getType().toString());
                    if (memberDesc.getName().getName().equals(memberName)) {
                        System.out.println("member name matches for this property!");

                    }
                }
            }
        }


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

                JetClass klass = QuickFixUtil.getParentElementOfType(diagnostic, JetClass.class);
                JetDelegationSpecifierList superClasses = PsiTreeUtil.getChildOfType(klass, JetDelegationSpecifierList.class);
                if (superClasses == null) {
                    return null;
                }

                //Fetch class descriptors for all super classes
                BindingContext contextClasses = KotlinCacheManagerUtil.getDeclarationsFromProject(superExp).getBindingContext();
                LinkedHashSet<MutableClassDescriptor> superClassDescs = new LinkedHashSet<MutableClassDescriptor>();
                for (JetDelegationSpecifier delSpec : superClasses.getDelegationSpecifiers()) {
                    JetSimpleNameExpression jetRef = PsiTreeUtil.findChildOfType(delSpec.getTypeReference(), JetSimpleNameExpression.class);
                    if (jetRef == null) {
                        continue;
                    }
                    MutableClassDescriptor classDesc = resolveToClass(jetRef, contextClasses);
                    if (classDesc != null) {
                        System.out.println("classDesc = " + classDesc);
                        superClassDescs.add(classDesc);
                    }
                }

                //Get the name of the member in question and other access information
                JetDotQualifiedExpression dotExp = QuickFixUtil.getParentElementOfType(diagnostic, JetDotQualifiedExpression.class);
                //TODO play around with array stuff...
                JetArrayAccessExpression arrayExp = PsiTreeUtil.getTopmostParentOfType(dotExp, JetArrayAccessExpression.class);
                JetValueArgumentList valArgs = null;
                String memberName = null;
                if (dotExp == null) {
                    return null;
                }
                JetCallExpression call = PsiTreeUtil.getChildOfType(dotExp, JetCallExpression.class);
                if (call != null) {
                    JetSimpleNameExpression name = PsiTreeUtil.getChildOfType(call, JetSimpleNameExpression.class);
                    if (name == null) {
                        return null;
                    }
                    memberName = name.getText();
                    valArgs = call.getValueArgumentList();
                }
                else {
                    JetSimpleNameExpression name = PsiTreeUtil.getChildOfType(dotExp, JetSimpleNameExpression.class);
                    if (name == null) {
                        return null;
                    }
                    memberName = name.getText();
                }
                System.out.println(memberName);

                //Get the type of the expression if applicable (e.g. var a : Int = super.foo)
                JetProperty assignment = PsiTreeUtil.getParentOfType(dotExp, JetProperty.class);
                JetTypeReference expectedType = null;
                if (assignment != null) {
                    expectedType = assignment.getTypeRef();
                }

                return new SpecifySuperExplicitlyFix(superExp, superClassDescs, dotExp, expectedType, memberName, valArgs);
            }
        };
    }

    /*Taken and modified from MapPlatformClassToKotlinFix*/
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
