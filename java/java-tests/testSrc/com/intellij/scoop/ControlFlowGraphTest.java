// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.scoop;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.slicer.ControlFlowGraph;
import com.intellij.slicer.ElementInstruction;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class ControlFlowGraphTest extends LightCodeInsightTestCase {

  // TODO: test the branches and loops are handled right
  public void testBuildControlFlowGraphForMethod() throws Exception {
    configureFromFileText("a.java", "public class Foo {\n" +
                                    "  public void foo() {\n" +
                                    "    int x = 1;\n" +
                                    "    if (x == 1) {\n" +
                                    "      x = 2;\n" +
                                    "    }\n" +
                                    "  }\n" +
                                    "}");
    final PsiCodeBlock body = ((PsiJavaFile)getFile()).getClasses()[0].getMethods()[0].getBody();
    ControlFlow flow =
      ControlFlowFactory.getInstance(getProject()).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    ControlFlowGraph graph = new ControlFlowGraph(flow);

    assertEquals(5, graph.size());

    ElementInstruction firstNode = graph.getNode(0);
    assertEquals(0, firstNode.getOffset());
    assertTrue(firstNode.getInstruction().getClass().toString().contains("WriteVariableInstruction"));
    assertEquals("int x = 1;", firstNode.getElement().getText());
    assertContainsElements(graph.getNext(0), 1);
  }
}
