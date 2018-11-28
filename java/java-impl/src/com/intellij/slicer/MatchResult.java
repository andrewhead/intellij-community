// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.psi.PsiElement;

import java.util.List;

public class MatchResult {
  private boolean mSuccess;
  private ElementInstruction mElementInstruction;
  private List<PsiElement> mMatchedElements;

  public MatchResult(boolean success, ElementInstruction elementInstruction, List<PsiElement> matchedElements) {
    mSuccess = success;
    mElementInstruction = elementInstruction;
    mMatchedElements = matchedElements;
  }

  public boolean getSuccess() { return mSuccess; }

  public ElementInstruction getElementInstruction() { return mElementInstruction; }

  public List<PsiElement> getMatchedElements() { return mMatchedElements; }
}
