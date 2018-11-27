// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.controlFlow.EmptyInstruction;
import com.intellij.psi.controlFlow.Instruction;

public class ElementInstruction {
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

  public static ElementInstruction getEndInstruction(int offset) {
    return new ElementInstruction(EmptyInstruction.INSTANCE, null, offset);
  }
}
