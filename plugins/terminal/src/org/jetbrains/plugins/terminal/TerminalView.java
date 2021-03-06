// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ToggleDistractionFreeModeAction;
import com.intellij.ide.actions.ToggleToolbarAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.JBTerminalWidgetListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.action.RenameTerminalSessionAction;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;
import org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author traff
 */
public class TerminalView {
  public final static Key<JBTerminalWidget> TERMINAL_WIDGET_KEY = new Key<>("TerminalWidget");

  private ToolWindow myToolWindow;
  private final Project myProject;
  private final LocalTerminalDirectRunner myTerminalRunner;
  private TerminalDockContainer myDockContainer;

  @NotNull
  public LocalTerminalDirectRunner getTerminalRunner() {
    return myTerminalRunner;
  }

  @Nullable
  private VirtualFile myFileToOpen;

  public TerminalView(Project project) {
    myProject = project;
    myTerminalRunner = LocalTerminalDirectRunner.createTerminalRunner(myProject);
  }

  public static TerminalView getInstance(@NotNull Project project) {
    return project.getComponent(TerminalView.class);
  }

  public void initTerminal(@NotNull ToolWindow toolWindow, @Nullable TerminalArrangementState arrangementState) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    myToolWindow = toolWindow;
    ((ToolWindowImpl)myToolWindow).setTabActions(new AnAction("New Session", "Create new session", AllIcons.General.Add) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        newTab(null);
      }
    });
    ((ToolWindowImpl)myToolWindow).setTabDoubleClickActions(new RenameTerminalSessionAction());

    myToolWindow.setToHideOnEmptyContent(true);
    List<TerminalTabState> states = arrangementState != null ? arrangementState.myTabStates : null;
    if (ContainerUtil.isEmpty(states)) {
      newTab(null);
    }
    else {
      for (TerminalTabState tabState : states) {
        createNewSession(myTerminalRunner, tabState);
      }
      ContentManager contentManager = toolWindow.getContentManager();
      Content content = contentManager.getContent(arrangementState.mySelectedTabIndex);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged() {
        if (toolWindow.isVisible()) {
          if (myToolWindow.getContentManager().getContentCount() == 0) {
            initTerminal(toolWindow, null);
          }
          myFileToOpen = null;
        }
      }
    });

    if (myDockContainer == null) {
      myDockContainer = new TerminalDockContainer(myToolWindow);
      Disposer.register(myProject, myDockContainer);
      DockManager.getInstance(myProject).register(myDockContainer);
    }
  }


  public void createNewSession(@NotNull AbstractTerminalRunner terminalRunner) {
    createNewSession(terminalRunner, null);
  }

  public void createNewSession(@NotNull AbstractTerminalRunner terminalRunner, @Nullable TerminalTabState tabState) {
    createNewTab(null, terminalRunner, myToolWindow, tabState);
  }

  private Content newTab(@Nullable JBTerminalWidget terminalWidget) {
    return createNewTab(terminalWidget, myTerminalRunner, myToolWindow, null);
  }

  @NotNull
  private Content createNewTab(@Nullable JBTerminalWidget terminalWidget,
                               @NotNull AbstractTerminalRunner terminalRunner,
                               @NotNull ToolWindow toolWindow,
                               @Nullable TerminalTabState tabState) {
    final Content content = createTerminalContent(terminalRunner, toolWindow, terminalWidget, tabState);
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
    return content;
  }

  private static String generateUniqueName(String suggestedName, List<String> tabs) {
    final Set<String> names = Sets.newHashSet(tabs);

    return UniqueNameGenerator.generateUniqueName(suggestedName, "", "", " (", ")", o -> !names.contains(o));
  }

  private Content createTerminalContent(@NotNull AbstractTerminalRunner terminalRunner,
                                        @NotNull ToolWindow toolWindow,
                                        @Nullable JBTerminalWidget terminalWidget,
                                        @Nullable TerminalTabState tabState) {
    TerminalToolWindowPanel panel = new TerminalToolWindowPanel(PropertiesComponent.getInstance(myProject), toolWindow);

    String tabName = ObjectUtils.notNull(tabState != null ? tabState.myTabName : null,
                                         TerminalOptionsProvider.Companion.getInstance().getTabName());

    Content[] contents = myToolWindow.getContentManager().getContents();

    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, tabName, false);
    if (terminalWidget == null) {
      VirtualFile currentWorkingDir = getCurrentWorkingDir(tabState);
      terminalWidget = terminalRunner.createTerminalWidget(content, currentWorkingDir);
      TerminalWorkingDirectoryManager.setInitialWorkingDirectory(content, currentWorkingDir);
    }
    else {
      terminalWidget.setVirtualFile(null);
      terminalWidget.moveDisposable(content);
    }

    JBTerminalWidget finalTerminalWidget = terminalWidget;
    terminalWidget.setListener(new JBTerminalWidgetListener() {
      @Override
      public void onNewSession() {
        newTab(null);
      }

      @Override
      public void onTerminalStarted() {
        if (tabState == null || StringUtil.isEmpty(tabState.myTabName)) {
          String name = finalTerminalWidget.getSettingsProvider().tabName(finalTerminalWidget.getTtyConnector(),
                                                                          finalTerminalWidget.getSessionName());

          content.setDisplayName(generateUniqueName(name, Arrays.stream(contents).map(c -> c.getDisplayName()).collect(Collectors.toList())));
        }
      }
    });

    content.setCloseable(true);
    content.putUserData(TERMINAL_WIDGET_KEY, terminalWidget);

    panel.setContent(terminalWidget.getComponent());
    panel.addFocusListener(createFocusListener());

    panel.uiSettingsChanged(null);

    content.setPreferredFocusableComponent(terminalWidget.getPreferredFocusableComponent());

    terminalWidget.addListener(widget -> {
      ApplicationManager.getApplication().invokeLater(() -> removeTab(content, true));
    });

    return content;
  }

  @Nullable
  private VirtualFile getCurrentWorkingDir(@Nullable TerminalTabState tabState) {
    String dir = tabState != null ? tabState.myWorkingDirectory : null;
    VirtualFile result = null;
    if (dir != null) {
      result = LocalFileSystem.getInstance().findFileByPath(dir);
    }
    if (result == null) {
      result = myFileToOpen;
    }
    return result;
  }

  private void removeTab(Content content, boolean keepFocus) {
    final ContentManager contentManager = myToolWindow.getContentManager();
    contentManager.removeContent(content, true, keepFocus, keepFocus);
  }

  private FocusListener createFocusListener() {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus();
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    };
  }

  private JComponent getComponentToFocus() {
    Content selectedContent = myToolWindow.getContentManager().getSelectedContent();
    if (selectedContent != null) {
      return selectedContent.getPreferredFocusableComponent();
    }
    else {
      return myToolWindow.getComponent();
    }
  }

  @Nullable
  public VirtualFile getFileToOpen() {
    return myFileToOpen;
  }

  public void setFileToOpen(@Nullable VirtualFile fileToOpen) {
    myFileToOpen = fileToOpen;
  }

  @NotNull
  public static JBTerminalWidget getWidgetByContent(@NotNull Content content) {
    return Objects.requireNonNull(content.getUserData(TERMINAL_WIDGET_KEY));
  }

  /**
   * @author traff
   */
  public class TerminalDockContainer implements DockContainer {
    private final ToolWindow myToolWindow;

    TerminalDockContainer(ToolWindow toolWindow) {
      myToolWindow = toolWindow;
    }

    @Override
    public RelativeRectangle getAcceptArea() {
      return new RelativeRectangle(myToolWindow.getComponent());
    }

    @Override
    public RelativeRectangle getAcceptAreaFallback() {
      return getAcceptArea();
    }

    @NotNull
    @Override
    public ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
      return isTerminalSessionContent(content) ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
    }

    @Override
    public JComponent getContainerComponent() {
      return myToolWindow.getComponent();
    }

    @Override
    public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
      if (isTerminalSessionContent(content)) {
        TerminalSessionVirtualFileImpl terminalFile = (TerminalSessionVirtualFileImpl)content.getKey();
        String name = terminalFile.getName();
        Content newContent = newTab(terminalFile.getTerminalWidget());
        newContent.setDisplayName(name);
      }
    }

    private boolean isTerminalSessionContent(DockableContent content) {
      return content.getKey() instanceof TerminalSessionVirtualFileImpl;
    }

    @Override
    public void closeAll() {

    }

    @Override
    public void addListener(Listener listener, Disposable parent) {

    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Nullable
    @Override
    public Image startDropOver(@NotNull DockableContent content, RelativePoint point) {
      return null;
    }

    @Nullable
    @Override
    public Image processDropOver(@NotNull DockableContent content, RelativePoint point) {
      return null;
    }

    @Override
    public void resetDropOver(@NotNull DockableContent content) {

    }

    @Override
    public boolean isDisposeWhenEmpty() {
      return false;
    }

    @Override
    public void showNotify() {

    }

    @Override
    public void hideNotify() {

    }

    @Override
    public void dispose() {

    }
  }
}


class TerminalToolWindowPanel extends SimpleToolWindowPanel implements UISettingsListener {
  private final PropertiesComponent myPropertiesComponent;
  private final ToolWindow myWindow;

  TerminalToolWindowPanel(PropertiesComponent propertiesComponent, ToolWindow window) {
    super(false, true);
    myPropertiesComponent = propertiesComponent;
    myWindow = window;
  }

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    updateDFState();
  }

  private void updateDFState() {
    if (isDfmSupportEnabled()) {
      setDistractionFree(shouldMakeDistractionFree());
    }
  }

  private void setDistractionFree(boolean isDistractionFree) {
    boolean isVisible = !isDistractionFree;
    setToolbarVisible(isVisible);
    setToolWindowHeaderVisible(isVisible);
  }

  private void setToolbarVisible(boolean isVisible) {
    ToggleToolbarAction.setToolbarVisible(myWindow, myPropertiesComponent, isVisible);
  }

  private void setToolWindowHeaderVisible(boolean isVisible) {
    InternalDecorator decorator = ((ToolWindowEx)myWindow).getDecorator();
    decorator.setHeaderVisible(isVisible);
  }

  private boolean shouldMakeDistractionFree() {
    return !myWindow.getAnchor().isHorizontal() && ToggleDistractionFreeModeAction.isDistractionFreeModeEnabled();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    updateDFState();
  }

  private static boolean isDfmSupportEnabled() {
    return Registry.get("terminal.distraction.free").asBoolean();
  }
}
