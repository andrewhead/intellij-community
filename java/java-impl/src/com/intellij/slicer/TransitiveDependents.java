// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.psi.PsiElement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TransitiveDependents {

  private Map<PsiElement, Set<PsiElement>> mDependents = new HashMap<>();

  public static TransitiveDependents from(DataFlowGraph dataFlowGraph) {
    TransitiveDependents that = new TransitiveDependents();
    for (DataFlowEdge edge : dataFlowGraph.getEdges()) {
      that.mDependents.put(edge.getFrom(), new HashSet<>());
    }
    boolean changed = true;
    while (changed) {
      changed = false;
      for (PsiElement node : that.mDependents.keySet()) {
        Set<PsiElement> nodeDeps = that.mDependents.get(node);
        int initialSize = nodeDeps.size();
        Set<PsiElement> nodesFrom = dataFlowGraph.getNodesWithEdgesFrom(node);
        for (PsiElement nodeFrom : nodesFrom) {
          nodeDeps.add(nodeFrom);
          Set<PsiElement> nodeToDeps = that.mDependents.get(nodeFrom);
          if (nodeToDeps != null) {
            nodeDeps.addAll(nodeToDeps);
            // For now, let's say a node can't have a dependency on itself.
            nodeDeps.remove(node);
          }
        }
        if (nodeDeps.size() > initialSize) {
          changed = true;
        }
      }
    }
    return that;
  }

  public Set<PsiElement> getTransitiveDependents(PsiElement element) {
    return mDependents.get(element);
  }

  public Set<PsiElement> getElements() {
    return mDependents.keySet();
  }
}
