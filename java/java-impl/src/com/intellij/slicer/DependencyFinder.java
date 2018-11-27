// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonProcessors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DependencyFinder {

  public static void getDependents(PsiElement element, Project project, List<PsiElement> dependents, List<Integer> processedUsages) {

    List<SliceUsage> newDependents = new ArrayList<>();

    if (processedUsages == null) {
      processedUsages = new ArrayList<>();
    }

    SliceAnalysisParams params = new SliceAnalysisParams();
    params.dataFlowToThis = true;
    params.scope = new AnalysisScope(project);

    final List<Integer> finalProcessedUsages = processedUsages;
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (element instanceof PsiReferenceExpression || element instanceof PsiIdentifier) {
          SliceUsage usage = LanguageSlicing.getProvider(element).createRootUsage(element, params);
          if (finalProcessedUsages.contains(usage.getUsageInfo().hashCode())) {
            return;
          }
          SliceUtil.processUsagesFlownDownTo(
            element, new CommonProcessors.CollectProcessor<>(newDependents),
            (JavaSliceUsage)usage, PsiSubstitutor.EMPTY, 0, ""
          );
          finalProcessedUsages.add(usage.getUsageInfo().hashCode());
        }
      }
    });

    // TODO: Consistently include declaration of arguments, or not.
    // TODO: A simplification type: simplify function signatures
    // TODO: also visit dependencies for object reference when field accessed
    // TODO: expand literal values
    // TODO: fix duplicates for literal values
    // TODO: include based on control flow
    // TODO: control dependencies
    // TODO: structural dependencies
    for (SliceUsage dependent : newDependents) {
      // Find all references in the dependent, and continue dependency analysis from there.
      PsiElement dependentElement = dependent.getElement();
      dependents.add(dependentElement);
      // Only call the following to recurse if you want to extract the complete slice:
      // getDependents(element, project, dependents, processedUsages);
    }
  }

  public static List<PsiElement> getDependencyElements(PsiElement element, Project project) {
    List<PsiElement> dependencies = new ArrayList<>();
    getDependents(element, project, dependencies, null);
    return dependencies;
  }

  private static Set<PsiStatement> getElementStatements(List<PsiElement> elements) {
    Set<PsiStatement> statements = new HashSet<>();
    for (PsiElement element : elements) {
      PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      if (parent != null) {
        statements.add((PsiStatement)parent);
      }
    }
    return statements;
  }

  public static Set<PsiStatement> getDependencies(PsiStatement statement, Project project) {
    List<PsiElement> dependencies = getDependencyElements(statement, project);
    return getElementStatements(dependencies);
  }
}
