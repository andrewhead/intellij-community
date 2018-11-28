// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InstructionSequenceSearch {
  public static Set<List<MatchResult>> search(ControlFlowGraph controlFlowGraph, InstructionMatcher... matchers) {
    Set<List<MatchResult>> subsequences = new HashSet<>();
    subsequences.add(new ArrayList<>());
    for (InstructionMatcher matcher : matchers) {
      Set<List<MatchResult>> newSubsequences = new HashSet<>();
      for (List<MatchResult> subsequence : subsequences) {
        MatchResult lastMatch = (subsequence.size() > 0) ? subsequence.get(subsequence.size() - 1) : null;
        ElementInstruction lastInstruction = lastMatch != null ? lastMatch.getElementInstruction() : null;
        for (MatchResult next : searchCfg(controlFlowGraph, matcher, lastInstruction, subsequence)) {
          List<MatchResult> newSubsequence = new ArrayList<>(subsequence);
          newSubsequence.add(next);
          newSubsequences.add(newSubsequence);
        }
      }
      subsequences = newSubsequences;
    }
    return subsequences;
  }

  public static Set<MatchResult> searchCfg(ControlFlowGraph controlFlowGraph,
                                           InstructionMatcher matcher,
                                           ElementInstruction startingAt,
                                           List<MatchResult> pastResults) {
    if (startingAt == null) {
      startingAt = controlFlowGraph.getNode(0);
    }
    Set<ElementInstruction> visited = new HashSet<>();
    Set<MatchResult> found = new HashSet<>();
    return searchCfg(controlFlowGraph, matcher, startingAt, visited, found, pastResults);
  }

  public static Set<MatchResult> searchCfg(ControlFlowGraph controlFlowGraph, InstructionMatcher matcher,
                                           ElementInstruction startAt,
                                           Set<ElementInstruction> visited,
                                           Set<MatchResult> found, List<MatchResult> pastResults) {
    if (visited.contains(startAt)) return found;
    MatchResult match = matcher.match(startAt, pastResults);
    if (match.getSuccess()) {
      found.add(match);
    }
    else {
      for (ElementInstruction next : controlFlowGraph.getNext(startAt)) {
        if (next != null) {
          found.addAll(searchCfg(controlFlowGraph, matcher, next, visited, found, pastResults));
        }
      }
    }
    visited.add(startAt);
    return found;
  }
}
