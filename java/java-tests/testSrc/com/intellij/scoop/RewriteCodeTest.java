// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.scoop;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.slicer.*;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.util.List;
import java.util.Set;

public class RewriteCodeTest extends LightCodeInsightTestCase {

  private static PsiElement getChild(PsiElement element, Class klazz) {
    for (PsiElement child : element.getChildren()) {
      if (klazz.isInstance(child)) {
        return child;
      }
    }
    return null;
  }

  public void testRewriteApiCall() throws Exception {
    configureFromFileText("a.java",
                          "import java.util.Collection;\n" +
                          "import java.util.List;\n" +
                          "import java.util.Stack;\n" +
                          "\n" +
                          "public class Foo {\n" +
                          "  public void foo() {\n" +
                          "    String str = \"Hello world!\";\n" +
                          "    Collection<String> c = new List<>();\n" +
                          "    c.add(str);\n" +
                          "    Stack<String> s = new Stack<>();\n" +
                          "    s.addAll(c);\n" +
                          "  }\n" +
                          "}");
    final PsiCodeBlock body = ((PsiJavaFile)getFile()).getClasses()[0].getMethods()[0].getBody();
    ControlFlow flow =
      ControlFlowFactory.getInstance(getProject()).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    ControlFlowGraph controlFlowGraph = new ControlFlowGraph(flow);

    // These matchers are all overly simplistic. Finding the right level of specification will be tricky. How are these pattern
    // detectors written in the IntelliJ code to be both precise and complete?
    // Part of this matching needs to make sure that these methods have no side-effects aside from those we care about; all of the
    // expressions we find should be the sole part of the statement that has a detectable effect.
    InstructionMatcher collectionInitMatcher = new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        PsiElement element = instruction.getElement();
        if (element == null) return false;
        System.out.println("Matcher 1: " + instruction.getElement().getClass() + ", " + instruction.getElement().getText());
        if (element instanceof PsiDeclarationStatement) {
          if (element.getFirstChild() instanceof PsiLocalVariable) {
            PsiLocalVariable localVariable = (PsiLocalVariable)element.getFirstChild();
            PsiType[] superTypes = localVariable.getTypeElement().getType().getSuperTypes();
            superTypes[superTypes.length - 1] = localVariable.getTypeElement().getType();
            for (PsiType superType : superTypes) {
              if (superType.getCanonicalText().startsWith("java.util.Collection")) {
                PsiNewExpression newExpression = (PsiNewExpression)getChild(localVariable, PsiNewExpression.class);
                if (newExpression != null) {
                  matchedElements.add(localVariable);
                  return true;
                }
              }
            }
          }
        }
        // return matchElementText(instruction, "Collection<String> c = new List<>();");
        return false;
      }
    };
    InstructionMatcher addMatcher = new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        PsiElement element = instruction.getElement();
        if (element == null) return false;
        System.out.println("Matcher 2: " + instruction.getElement().getClass() + ", " + instruction.getElement().getText());
        if (element instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)element;
          PsiReferenceExpression ref = call.getMethodExpression();
          PsiReferenceExpression objectRef = (PsiReferenceExpression)getChild(ref, PsiReferenceExpression.class);
          PsiIdentifier methodIdentifier = (PsiIdentifier)getChild(ref, PsiIdentifier.class);
          // PsiIdentifier methodId = ref.getQualifiedName();
          if (objectRef != null) {
            PsiElement def = objectRef.getReference().resolve();
            // TODO: Add a bunch more checking here
            PsiElement otherDef = pastMatches.get(0).getMatchedElements().get(0);
            if (otherDef.isEquivalentTo(def)) {
              if (methodIdentifier.getText().equals("add")) {
                if (call.getArgumentList().getExpressions().length == 1) {
                  PsiExpression argExpression = call.getArgumentList().getExpressions()[0];
                  matchedElements.add(argExpression);
                  return true;
                }
              }
            }
          }
        }
        // return matchElementText(instruction, "c.add(str)");
        return false;
      }
    };
    InstructionMatcher stackAddAllMatcher = new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        PsiElement element = instruction.getElement();
        if (element == null) return false;
        System.out.println("Matcher 3: " + instruction.getElement().getClass() + ", " + instruction.getElement().getText());
        if (element instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)element;
          PsiReferenceExpression ref = call.getMethodExpression();
          PsiReferenceExpression objectRef = (PsiReferenceExpression)getChild(ref, PsiReferenceExpression.class);
          PsiIdentifier methodIdentifier = (PsiIdentifier)getChild(ref, PsiIdentifier.class);
          if (objectRef != null) {
            if (objectRef.getType().getCanonicalText().startsWith("java.util.Stack")) {
              PsiElement def = objectRef.getReference().resolve();
              matchedElements.add(def);
              if (methodIdentifier.getText().equals("addAll")) {
                if (call.getArgumentList().getExpressions().length == 1) {
                  PsiExpression argExpression = call.getArgumentList().getExpressions()[0];
                  // TODO: Add a bunch more checking here
                  PsiElement otherDef = pastMatches.get(0).getMatchedElements().get(0);
                  return argExpression.getReference().resolve().isEquivalentTo(otherDef);
                }
              }
            }
          }
        }
        // return matchElementText(instruction, "s.addAll(c)");
        return false;
      }
    };

    Set<List<MatchResult>> matches =
      InstructionSequenceSearch.search(controlFlowGraph, collectionInitMatcher, addMatcher, stackAddAllMatcher);
    assertEquals(1, matches.size());

    /*
    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        Stack<String> s = new Stack<>();
        if (element instanceof PsiStatement && element.getText().contains("Collection")) {
          System.out.println("Hiya");
        }
      }
    };
    body.accept(visitor);
    */
  }
}
