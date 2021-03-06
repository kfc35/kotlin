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

package org.jetbrains.jet.completion.confidence;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionTestCase;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.commons.lang.SystemUtils;
import org.jetbrains.jet.plugin.PluginTestCaseBase;
import org.jetbrains.jet.InTextDirectivesUtils;

import java.io.File;

public class JetConfidenceTest extends LightCompletionTestCase {
    private static final String TYPE_DIRECTIVE_PREFIX = "// TYPE:";

    public void testCompleteOnDotOutOfRanges() {
        doTest();
    }

    public void testImportAsConfidence() {
        doTest();
    }

    public void testInBeginningOfFunctionLiteral() {
        doTest();
    }

    public void testInBeginningOfFunctionLiteralInBrackets() {
        doTest();
    }

    public void testInBlockOfFunctionLiteral() {
        doTest();
    }

    public void testInModifierList() {
        doTest();
    }

    public void testNoAutoCompletionForRangeOperator() {
        doTest();
    }

    protected void doTest() {
        configureByFile(getBeforeFileName());
        type(getTypeTextFromFile());
        checkResultByFile(getAfterFileName());
    }

    protected static String getTypeTextFromFile() {
        String text = getEditor().getDocument().getText();

        String[] directives = InTextDirectivesUtils.findArrayWithPrefix(TYPE_DIRECTIVE_PREFIX, text);
        assertEquals("One directive with \"" + TYPE_DIRECTIVE_PREFIX +"\" expected", 1, directives.length);

        return StringUtil.unquoteString(directives[0]);
    }

    protected String getBeforeFileName() {
        return getTestName(false) + ".kt";
    }

    protected String getAfterFileName() {
        return getTestName(false) + ".kt.after";
    }

    @Override
    protected String getTestDataPath() {
        return new File(PluginTestCaseBase.getTestDataPathBase(), "/completion/confidence/").getPath() + File.separator;
    }

    @Override
    protected Sdk getProjectJDK() {
        return JavaSdk.getInstance().createJdk("JDK", SystemUtils.getJavaHome().getAbsolutePath());
    }

    @Override
    protected void complete() {
        new CodeCompletionHandlerBase(CompletionType.BASIC, false, true, true).invokeCompletion(
                getProject(), getEditor(), 0, false, false);
    }
}