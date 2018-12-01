// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

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

  private static List<PsiElement> getContainingSequence(PsiElement hoveredElement, Set<List<MatchResult>> matches) {

    PsiElement parent = hoveredElement;
    List<MatchResult> sequenceToHighlight = null;
    while (parent != null) {
      for (List<MatchResult> sequence : matches) {
        for (MatchResult matchResult : sequence) {
          if (matchResult.getElementInstruction().getElement().isEquivalentTo(parent)) {
            sequenceToHighlight = sequence;
            break;
          }
        }
        if (sequenceToHighlight != null) {
          break;
        }
      }
      if (sequenceToHighlight != null) {
        break;
      }
      parent = parent.getParent();
    }

    List<PsiElement> sequenceElements = new ArrayList<>();
    if (sequenceToHighlight != null) {
      for (MatchResult matchResult : sequenceToHighlight) {
        sequenceElements.add(matchResult.getElementInstruction().getElement());
      }
    }
    return sequenceElements;
  }

  private static PsiElement getChild(PsiElement element, Class klazz) {
    for (PsiElement child : element.getChildren()) {
      if (klazz.isInstance(child)) {
        return child;
      }
    }
    return null;
  }

  private void crazyStuff(Project project, Editor editor, PsiFile psiFile) {
    // These matchers are all overly simplistic. Finding the right level of specification will be tricky. How are these pattern
    // detectors written in the IntelliJ code to be both precise and complete?
    // Part of this matching needs to make sure that these methods have no side-effects aside from those we care about; all of the
    // expressions we find should be the sole part of the statement that has a detectable effect.
    InstructionMatcher collectionInitMatcher = new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        PsiElement element = instruction.getElement();
        if (element == null) return false;
        System.out.println("Matcher 1: " + instruction.getElement().getClass() + ", " + instruction.getElement().getText());
        if (element instanceof PsiDeclarationStatement) {
          if (element.getFirstChild() instanceof PsiLocalVariable) {
            PsiLocalVariable localVariable = (PsiLocalVariable)element.getFirstChild();
            PsiType[] superTypes = localVariable.getTypeElement().getType().getSuperTypes();
            superTypes[superTypes.length - 1] = localVariable.getTypeElement().getType();
            for (PsiType superType : superTypes) {
              if (superType.getCanonicalText().startsWith("java.util.Collection")) {
                PsiNewExpression newExpression = (PsiNewExpression)getChild(localVariable, PsiNewExpression.class);
                if (newExpression != null) {
                  matchedElements.add(localVariable);
                  return true;
                }
              }
            }
          }
        }
        // return matchElementText(instruction, "Collection<String> c = new List<>();");
        return false;
      }
    };
    InstructionMatcher addMatcher = new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        PsiElement element = instruction.getElement();
        if (element == null) return false;
        System.out.println("Matcher 2: " + instruction.getElement().getClass() + ", " + instruction.getElement().getText());
        if (element instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)element;
          PsiReferenceExpression ref = call.getMethodExpression();
          PsiReferenceExpression objectRef = (PsiReferenceExpression)getChild(ref, PsiReferenceExpression.class);
          PsiIdentifier methodIdentifier = (PsiIdentifier)getChild(ref, PsiIdentifier.class);
          // PsiIdentifier methodId = ref.getQualifiedName();
          if (objectRef != null) {
            PsiElement def = objectRef.getReference().resolve();
            // TODO: Add a bunch more checking here
            PsiElement otherDef = pastMatches.get(0).getMatchedElements().get(0);
            if (otherDef.isEquivalentTo(def)) {
              if (methodIdentifier.getText().equals("add")) {
                if (call.getArgumentList().getExpressions().length == 1) {
                  PsiExpression argExpression = call.getArgumentList().getExpressions()[0];
                  matchedElements.add(argExpression);
                  return true;
                }
              }
            }
          }
        }
        // return matchElementText(instruction, "c.add(str)");
        return false;
      }
    };
    InstructionMatcher stackAddAllMatcher = new InstructionMatcher() {
      @Override
      public boolean doMatch(ElementInstruction instruction, List<MatchResult> pastMatches, List<PsiElement> matchedElements) {
        PsiElement element = instruction.getElement();
        if (element == null) return false;
        System.out.println("Matcher 3: " + instruction.getElement().getClass() + ", " + instruction.getElement().getText());
        if (element instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)element;
          PsiReferenceExpression ref = call.getMethodExpression();
          PsiReferenceExpression objectRef = (PsiReferenceExpression)getChild(ref, PsiReferenceExpression.class);
          PsiIdentifier methodIdentifier = (PsiIdentifier)getChild(ref, PsiIdentifier.class);
          if (objectRef != null) {
            if (objectRef.getType().getCanonicalText().startsWith("java.util.Stack")) {
              PsiElement def = objectRef.getReference().resolve();
              matchedElements.add(def);
              if (methodIdentifier.getText().equals("addAll")) {
                if (call.getArgumentList().getExpressions().length == 1) {
                  PsiExpression argExpression = call.getArgumentList().getExpressions()[0];
                  // TODO: Add a bunch more checking here
                  PsiElement otherDef = pastMatches.get(0).getMatchedElements().get(0);
                  return argExpression.getReference().resolve().isEquivalentTo(otherDef);
                }
              }
            }
          }
        }
        // return matchElementText(instruction, "s.addAll(c)");
        return false;
      }
    };

    final Set<List<MatchResult>> matches = new HashSet<>();
    final PsiCodeBlock body = ((PsiJavaFile)psiFile).getClasses()[0].getMethods()[0].getBody();
    try {
      ControlFlow flow =
        ControlFlowFactory.getInstance(project).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
      ControlFlowGraph controlFlowGraph = new ControlFlowGraph(flow);
      matches.addAll(
        InstructionSequenceSearch.search(controlFlowGraph, collectionInitMatcher, addMatcher, stackAddAllMatcher));
    }
    catch (Exception e) {
      System.out.println("Caught exception: " + e.toString());
    }

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

            for (PsiElement elementToHighlight : getContainingSequence(hoveredElement, matches)) {
              RangeHighlighter highlighter = Objects.requireNonNull(editor).getMarkupModel().addRangeHighlighter(
                elementToHighlight.getTextRange().getStartOffset(), elementToHighlight.getTextRange().getEndOffset(), 0,
                new TextAttributes(null, new JBColor(new Color(153, 204, 255), new Color(153, 204, 255)), null, null, -1),
                HighlighterTargetArea.EXACT_RANGE);
              this.mOldHighlighters.add(highlighter);
            }
          }
        }

        @Override
        public void mouseDragged(EditorMouseEvent e) {}
      }
    );

    editor.addEditorMouseListener(new EditorMouseListener() {
      @Override
      public void mousePressed(EditorMouseEvent e) {}

      @Override
      public void mouseClicked(EditorMouseEvent e) {
        // TODO: need to make these deletions undoable
        LogicalPosition mousePosition = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
        int offset = editor.logicalPositionToOffset(mousePosition);
        final PsiElement hoveredElement = psiFile.findElementAt(offset);
        if (hoveredElement != null) {

          PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
          PsiMethodCallExpression newLine =
            (PsiMethodCallExpression)factory.createExpressionFromText("s.addElement(str)", null);

          final List<PsiElement> toReplace = getContainingSequence(hoveredElement, matches);
          CommandProcessor.getInstance().executeCommand(new Runnable() {
            @Override
            public void run() {
              for (int i = 0; i < toReplace.size(); i++) {
                if (i < toReplace.size() - 1) {
                  toReplace.get(i).delete();
                }
                else {
                  toReplace.get(i).replace(newLine);
                }
              }
            }
          }, "hello", 0);
        }
      }

      @Override
      public void mouseReleased(EditorMouseEvent e) {}

      @Override
      public void mouseEntered(EditorMouseEvent e) {}

      @Override
      public void mouseExited(EditorMouseEvent e) {}
    });
  }

  @Override
  public void actionPerformed(AnActionEvent event) {

    final Project project = event.getProject();
    final Editor editor = event.getData(CommonDataKeys.EDITOR);
    final PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);

    crazyStuff(project, editor, psiFile);
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
