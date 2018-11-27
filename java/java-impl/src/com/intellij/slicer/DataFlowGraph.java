// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiStatement;

import java.util.HashSet;
import java.util.Set;

public class DataFlowGraph {
  private Set<DataFlowEdge> mEdges = new HashSet<>();

  public void addEdge(PsiElement from, PsiElement to) {
    DataFlowEdge edge = new DataFlowEdge(from, to);
    mEdges.add(edge);
  }

  public Set<PsiElement> getNodesWithEdgesFrom(PsiElement element) {
    Set<PsiElement> nodesFrom = new HashSet<>();
    for (DataFlowEdge edge : getEdges()) {
      if (edge.getFrom() == element) {
        nodesFrom.add(edge.getTo());
      }
    }
    return nodesFrom;
  }

  public Set<DataFlowEdge> getEdges() {
    return mEdges;
  }

  public static DataFlowGraph buildDataFlowGraph(PsiFile psiFile) {
    DataFlowGraph dataFlowGraph = new DataFlowGraph();
    Set<PsiStatement> statements = collectStatements(psiFile);
    for (PsiStatement statement : statements) {
      Set<PsiStatement> dependencies = DependencyFinder.getDependencies(statement, psiFile.getProject());
      for (PsiStatement dependency : dependencies) {
        if (statement != dependency) {
          dataFlowGraph.addEdge(dependency, statement);
        }
      }
    }
    return dataFlowGraph;
  }

  private static Set<PsiStatement> collectStatements(PsiFile psiFile) {
    Set<PsiStatement> statements = new HashSet<>();
    psiFile.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (element instanceof PsiStatement) {
          statements.add((PsiStatement)element);
        }
      }
    });
    return statements;
  }
}
