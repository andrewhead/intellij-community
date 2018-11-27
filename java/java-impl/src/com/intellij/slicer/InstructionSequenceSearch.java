// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InstructionSequenceSearch {
  public static Set<List<ElementInstruction>> search(ControlFlowGraph controlFlowGraph, InstructionMatcher... matchers) {
    Set<List<ElementInstruction>> subsequences = new HashSet<>();
    subsequences.add(new ArrayList<>());
    for (InstructionMatcher matcher : matchers) {
      Set<List<ElementInstruction>> newSubsequences = new HashSet<>();
      for (List<ElementInstruction> subsequence : subsequences) {
        ElementInstruction lastInstructionInPattern = (subsequence.size() > 0) ? subsequence.get(subsequence.size() - 1) : null;
        for (ElementInstruction next : controlFlowGraph.search(matcher, lastInstructionInPattern)) {
          List<ElementInstruction> newSubsequence = new ArrayList<>(subsequence);
          newSubsequence.add(next);
          newSubsequences.add(newSubsequence);
        }
      }
      subsequences = newSubsequences;
    }
    return subsequences;
  }
}
