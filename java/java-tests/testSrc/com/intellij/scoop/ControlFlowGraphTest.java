// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.scoop;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.controlFlow.*;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ControlFlowGraphTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/psi/controlFlow";


  private class ElementInstruction {
    private Instruction instruction;
    private PsiElement element;
    private int offset;

    public ElementInstruction(Instruction instruction, PsiElement element, int offset) {
      this.instruction = instruction;
      this.element = element;
      this.offset = offset;
    }

    public Instruction getInstruction() { return this.instruction; }

    public PsiElement getElement() { return this.element; }

    public int getOffset() { return this.offset; }
  }

  private class ControlFlowGraph {

    private Map<Integer, ElementInstruction> mNodes = new HashMap<>();
    private Map<Integer, Set<Integer>> mEdges = new HashMap<>();

    public ControlFlowGraph buildFromControlFlow(ControlFlow flow) {
      for (int i = 0; i < flow.getInstructions().size(); i++) {
        Instruction instruction = flow.getInstructions().get(i);
        ElementInstruction elementInstruction = getElementInstruction(flow, i);
        this.addNode(elementInstruction);
        for (int n = 0; n < instruction.nNext(); n++) {
          ElementInstruction next = getElementInstruction(flow, instruction.getNext(i, n));
          this.addNode(next);
          this.addEdge(elementInstruction, next);
        }
      }
      return this;
    }

    public void addNode(ElementInstruction node) {
      this.mNodes.put(node.getOffset(), node);
    }

    public void addEdge(ElementInstruction node1, ElementInstruction node2) {
      if (!this.mEdges.containsKey(node1.getOffset())) {
        this.mEdges.put(node1.getOffset(), new HashSet<>());
      }
      this.mEdges.get(node1.getOffset()).add(node2.getOffset());
    }

    public ElementInstruction getNode(int offset) {
      return this.mNodes.get(offset);
    }

    public int size() {
      return this.mNodes.size();
    }

    public Set<Integer> getNext(int offset) {
      return this.mEdges.get(offset);
    }
  }

  private ElementInstruction getEndInstruction(int offset) {
    return new ElementInstruction(EmptyInstruction.INSTANCE, null, offset);
  }

  private ElementInstruction getElementInstruction(ControlFlow flow, int offset) {
    if (offset < flow.getInstructions().size()) {
      return new ElementInstruction(
        flow.getInstructions().get(offset), flow.getElement(offset), offset
      );
    }
    else {
      return getEndInstruction(offset);
    }
  }

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
    ControlFlowGraph graph = new ControlFlowGraph().buildFromControlFlow(flow);

    assertEquals(5, graph.size());

    ElementInstruction firstNode = graph.getNode(0);
    assertEquals(0, firstNode.getOffset());
    assertTrue(firstNode.getInstruction().getClass().toString().contains("WriteVariableInstruction"));
    assertEquals("int x = 1;", firstNode.getElement().getText());
    assertContainsElements(graph.getNext(0), 1);
  }

  // TODO: test the branches and loops are handled right
}
