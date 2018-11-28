// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.Instruction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ControlFlowGraph {

  private Map<Integer, ElementInstruction> mNodes = new HashMap<>();
  private Map<ElementInstruction, Integer> mNodeIndexes = new HashMap<>();
  private Map<Integer, Set<Integer>> mEdges = new HashMap<>();
  private ControlFlow mFlow;

  public ControlFlowGraph(ControlFlow flow) {
    this.mFlow = flow;
    for (int i = 0; i < flow.getInstructions().size(); i++) {
      Instruction instruction = flow.getInstructions().get(i);
      ElementInstruction elementInstruction = getElementInstruction(i);
      this.addNode(elementInstruction);
      for (int n = 0; n < instruction.nNext(); n++) {
        ElementInstruction next = getElementInstruction(instruction.getNext(i, n));
        this.addNode(next);
        this.addEdge(elementInstruction, next);
      }
    }
  }

  public void addNode(ElementInstruction node) {
    this.mNodes.put(node.getOffset(), node);
    this.mNodeIndexes.put(node, node.getOffset());
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

  private ElementInstruction getElementInstruction(int offset) {
    if (offset < this.mFlow.getInstructions().size()) {
      return new ElementInstruction(
        this.mFlow.getInstructions().get(offset), this.mFlow.getElement(offset), offset
      );
    }
    else {
      return ElementInstruction.getEndInstruction(offset);
    }
  }

  public Set<Integer> getNext(int offset) {
    return this.mEdges.containsKey(offset) ? this.mEdges.get(offset) : new HashSet<>();
  }

  public Set<ElementInstruction> getNext(ElementInstruction elementInstruction) {
    int nodeIndex = this.mNodeIndexes.get(elementInstruction);
    Set<Integer> nextIndexes = getNext(nodeIndex);
    Set<ElementInstruction> nextInstructions = new HashSet<>();
    for (int nextIndex : nextIndexes) {
      nextInstructions.add(this.mNodes.get(nextIndex));
    }
    return nextInstructions;
  }
}
