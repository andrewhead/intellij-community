// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.slicer;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.slicer.DataFlowEdge;
import com.intellij.slicer.DataFlowGraph;
import com.intellij.slicer.TransitiveDependents;

import java.util.Set;

/**
 * @author andrewhead
 */
public class DataFlowGraphTest extends SliceTestCase {

  private static void assertContainsEdge(DataFlowGraph dataFlowGraph, String from, String to) {
    for (DataFlowEdge edge : dataFlowGraph.getEdges()) {
      if (edge.getFrom().getText().equals(from) && edge.getTo().getText().equals(to)) {
        return;
      }
    }
    fail();
  }

  private static void assertHasNDependencies(TransitiveDependents dependencies, String statementText, int count) {
    Set<PsiElement> elements = dependencies.getElements();
    for (PsiElement element : elements) {
      if (element.getText().equals(statementText)) {
        Set<PsiElement> transitiveDependencies = dependencies.getTransitiveDependents(element);
        assertEquals(count, transitiveDependencies.size());
        return;
      }
    }
    fail();
  }

  private void doTest() throws Exception {

    configureByFile("/scoop/" + getTestName(false) + ".java");
    Document document = getEditor().getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);

    DataFlowGraph dataFlowGraph = DataFlowGraph.buildDataFlowGraph(psiFile);

    assertEquals(2, dataFlowGraph.getEdges().size());
    assertContainsEdge(dataFlowGraph, "int y = x + 1;", "return y;");
    assertContainsEdge(dataFlowGraph, "int x = 1;", "int y = x + 1;");

    TransitiveDependents dependencies = TransitiveDependents.from(dataFlowGraph);
    assertHasNDependencies(dependencies, "int x = 1;", 2);
    assertHasNDependencies(dependencies, "int y = x + 1;", 1);
  }

  public void testSimple() throws Exception { doTest();}
}
