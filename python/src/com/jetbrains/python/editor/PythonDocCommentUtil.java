/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.editor;

import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;

/**
 * User : catherine
 */
public class PythonDocCommentUtil {

  private PythonDocCommentUtil() {
  }

  static public boolean atDocCommentStart(PsiElement element, int offset) {
    PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (string != null) {
      PyElement func = PsiTreeUtil.getParentOfType(element, PyFunction.class, PyClass.class, PyFile.class);
      if (func != null) {
        final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element,
                                                                            PyDocStringOwner.class);
        if (docStringOwner == func) {
          PyStringLiteralExpression str = docStringOwner.getDocStringExpression();
          String text = element.getText();
          final int prefix = PyStringLiteralExpressionImpl.getPrefixLength(text);
          text = text.substring(prefix);
          if (str != null && text.equals(str.getText()) &&
                      (text.startsWith("\"\"\"") || text.startsWith("'''"))) {
            if (offset == str.getTextRange().getStartOffset()) {
              PsiErrorElement error = PsiTreeUtil.getNextSiblingOfType(string, PsiErrorElement.class);
              if (error != null)
                return true;
              error = PsiTreeUtil.getNextSiblingOfType(string.getParent(), PsiErrorElement.class);
              if (error != null)
                return true;

              if (text.length() < 6 || (!text.endsWith("\"\"\"") && !text.endsWith("'''")))
                return true;
            }
          }
        }
      }
    }
    return false;
  }

  static public String removeParamFromDocstring(String text, String prefix, String paramName) {
    StringBuilder newText = new StringBuilder();
    String[] lines = LineTokenizer.tokenize(text, true);
    boolean skipNext = false;
    for (String line : lines) {
      if (line.contains(prefix)) {
        String[] subLines = line.split(" ");
        boolean lookNext = false;
        boolean add = true;
        for (String s : subLines) {
          final String trimmedLine = s.trim();
          if (trimmedLine.equals(prefix + "param") || trimmedLine.equals(prefix + "type")) {
            lookNext = true;
          }
          if (lookNext && trimmedLine.endsWith(":")) {
            String tmp = trimmedLine.substring(0, trimmedLine.length() - 1);
            if (paramName.equals(tmp)) {
              lookNext = false;
              skipNext = true;
              add = false;
            }
          }
        }
        if (add) {
          newText.append(line);
          skipNext = false;
        }
      }
      else if (!skipNext || line.contains("\"\"\"") || line.contains("'''")) {
        newText.append(line);
      }
    }
    return newText.toString();
  }
}
