// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.psi.PsiElement;

public class DataFlowEdge {
  private PsiElement mFrom;
  private PsiElement mTo;

  public DataFlowEdge(PsiElement from, PsiElement to) {
    mFrom = from;
    mTo = to;
  }

  public PsiElement getFrom() {
    return mFrom;
  }

  public PsiElement getTo() {
    return mTo;
  }

  public String toString() {
    return this.getFrom().getText() + " ==> " + this.getTo().getText();
  }
}
