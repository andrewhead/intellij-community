// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.scoop;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.slicer.*;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SequenceSearchTest extends LightCodeInsightTestCase {

  private static ControlFlowGraph prepareControlFlowGraph() throws Exception {
    configureFromFileText("a.java", "public class Foo {\n" +
                                    "  public void foo() {\n" +
                                    "    int assign1 = 1;\n" +
                                    "    if (x == 1) {\n" +
                                    "      int assign2 = 2;\n" +
                                    "    }\n" +
                                    "  }\n" +
                                    "}");
    final PsiCodeBlock body = ((PsiJavaFile)getFile()).getClasses()[0].getMethods()[0].getBody();
    ControlFlow flow =
      ControlFlowFactory.getInstance(getProject()).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    return new ControlFlowGraph(flow);
  }

  private static boolean matchElementText(ElementInstruction elementInstruction, String text) {
    PsiElement element = elementInstruction.getElement();
    if (element != null && element.getText() != null) {
      return element.getText().equals(text);
    }
    return false;
  }

  private static InstructionMatcher makeTextMatcher(String text) {
    return new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction elementInstruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        return matchElementText(elementInstruction, text);
      }
    };
  }

  // TODO: find multiple sequences
  // TODO: branching sequence (call1, then call2, then call2 again. Or it appears in both branches).
  public void testFindSequence() throws Exception {
    ControlFlowGraph controlFlowGraph = prepareControlFlowGraph();
    Set<List<MatchResult>> instructionSets =
      InstructionSequenceSearch.search(controlFlowGraph, makeTextMatcher("int assign1 = 1;"), makeTextMatcher("int assign2 = 2;"));
    assertEquals(1, instructionSets.size());
    List<MatchResult> matches = Collections.list(Collections.enumeration(instructionSets)).get(0);
    assertEquals("int assign1 = 1;", matches.get(0).getElementInstruction().getElement().getText());
    assertEquals("int assign2 = 2;", matches.get(1).getElementInstruction().getElement().getText());
  }

  public void testFindSequenceWithMatchContext() throws Exception {
    ControlFlowGraph controlFlowGraph = prepareControlFlowGraph();
    InstructionMatcher firstMatcher = new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        boolean match = matchElementText(instruction, "int assign1 = 1;");
        if (match) {
          matchedElements.add(instruction.getElement());
        }
        return match;
      }
    };
    InstructionMatcher secondMatcher = new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        return matchElementText(instruction, "int assign2 = 2;") &&
               pastMatches.size() >= 1 &&
               pastMatches.get(0).getMatchedElements().size() >= 0 &&
               pastMatches.get(0).getMatchedElements().get(0).getText().equals("int assign1 = 1;");
      }
    };
    Set<List<MatchResult>> instructionSets =
      InstructionSequenceSearch.search(controlFlowGraph, firstMatcher, secondMatcher);
    assertEquals(1, instructionSets.size());
  }

  public void testDontFindSequence() throws Exception {
    ControlFlowGraph controlFlowGraph = prepareControlFlowGraph();
    Set<List<MatchResult>> instructionSets =
      InstructionSequenceSearch.search(controlFlowGraph, makeTextMatcher("int assign1 = 1;"), makeTextMatcher("line doesn't exist"));
    assertEquals(0, instructionSets.size());
  }
}
