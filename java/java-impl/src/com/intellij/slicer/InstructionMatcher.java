// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

public abstract class InstructionMatcher {

  public MatchResult match(ElementInstruction instruction, List<MatchResult> pastMatches) {
    List<PsiElement> matchedElements = new ArrayList<>();
    boolean matched = doMatch(instruction, pastMatches, matchedElements);
    return new MatchResult(matched, instruction, matchedElements);
  }

  /**
   * @param instruction     Instruction to do matching on
   * @param pastMatches     The matches that have come before this match (context for context-sensitive matches)
   * @param matchedElements Array to be filled with elements that this rule wants to make available to later matchers
   * @return true if the instruction matches
   */
  public abstract boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements);
}
