// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author cdr
 */
public class ScoopAction extends CodeInsightAction {

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new SliceHandler(true);
  }

  @Override
  public void update(AnActionEvent e) {
    if (LanguageSlicing.hasAnyProviders()) {
      super.update(e);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return LanguageSlicing.getProvider(file) != null;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {

    final Editor editor = event.getData(CommonDataKeys.EDITOR);
    final PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);

    editor.addEditorMouseMotionListener(
      new EditorMouseMotionListener() {
        private List<RangeHighlighter> mOldHighlighters = new ArrayList<>();

        @Override
        public void mouseMoved(EditorMouseEvent e) {
          LogicalPosition mousePosition = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
          int offset = editor.logicalPositionToOffset(mousePosition);
          PsiElement hoveredElement = psiFile.findElementAt(offset);
          if (hoveredElement != null) {
            for (RangeHighlighter oldHighlighter : this.mOldHighlighters) {
              Objects.requireNonNull(editor).getMarkupModel().removeHighlighter(oldHighlighter);
            }
            this.mOldHighlighters.clear();
            RangeHighlighter highlighter = Objects.requireNonNull(editor).getMarkupModel().addRangeHighlighter(
              hoveredElement.getTextRange().getStartOffset(), hoveredElement.getTextRange().getEndOffset(), 0,
              new TextAttributes(null, new JBColor(new Color(153, 204, 255), new Color(153, 204, 255)), null, null, -1),
              HighlighterTargetArea.EXACT_RANGE);
            this.mOldHighlighters.add(highlighter);
          }
        }

        @Override
        public void mouseDragged(EditorMouseEvent e) {}
      }
    );

    // There is tons of weird stuff going on with data flow analysis for objects---it seems to make bidirectional relationships,
    // which does not seem right. Maybe it's because the selection is wrong?

    DataFlowGraph dataFlowGraph = DataFlowGraph.buildDataFlowGraph(psiFile);
    TransitiveDependents
      transitiveDependents = TransitiveDependents.from(dataFlowGraph);
    for (PsiElement element : transitiveDependents.getElements()) {
      Set<PsiElement> dependents = transitiveDependents.getTransitiveDependents(element);
      int colorDepth = dependents.size();
      int green = Math.max(255 - colorDepth * 20, 0);
      int blue = Math.max(255 - colorDepth * 20, 0);
      Objects.requireNonNull(editor).getMarkupModel().addRangeHighlighter(
        element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset(), 0,
        new TextAttributes(null, new JBColor(new Color(255, green, blue), new Color(255, green, blue)), null, null, -1),
        HighlighterTargetArea.EXACT_RANGE);
      // System.out.println(element.getText() + ": " + dependents.size());
    }

    /*
    PsiElement element = new SliceHandler(true).getExpressionAtCaret(editor, psiFile);
    final List<PsiElement> descendants = new ArrayList<>();
    getDependents(element, project, descendants, null);
    // Collect the statements that contain each of the dependents.
    for (PsiElement descendant: descendants) {
      PsiElement parent = PsiTreeUtil.getParentOfType(descendant, PsiStatement.class);
      String text = parent == null ? descendant.getText() : parent.getText();
      int lineNumber = parent == null ?
                       document.getLineNumber(descendant.getTextOffset()) :
                       document.getLineNumber(parent.getTextOffset());
      System.out.println(lineNumber + 1 + ": " + text);
    }
    */
  }
}
