/*
 * Copyright 2021 Yu Junyang
 * https://github.com/lowkeyfish
 *
 * This file is part of Sonar Intellij plugin.
 *
 * Sonar Intellij plugin is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Sonar Intellij plugin is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Sonar Intellij plugin.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.intellij.plugin.sonar.core;

import java.util.concurrent.atomic.AtomicBoolean;

import com.github.intellij.plugin.sonar.common.EventDispatchThreadHelper;
import com.github.intellij.plugin.sonar.common.LogUtils;
import com.github.intellij.plugin.sonar.common.SettingsUtils;
import com.github.intellij.plugin.sonar.common.exceptions.ConfigException;
import com.github.intellij.plugin.sonar.extensions.ToolWindowFactoryImpl;
import com.github.intellij.plugin.sonar.gui.common.BalloonTipFactory;
import com.github.intellij.plugin.sonar.messages.AnalysisAbortingListener;
import com.github.intellij.plugin.sonar.messages.MessageBusManager;
import com.github.intellij.plugin.sonar.resources.ResourcesLoader;
import com.github.intellij.plugin.sonar.service.ProblemCacheService;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.scanner.api.LogOutput;
import org.sonarsource.scanner.api.internal.ScannerException;

public abstract class SonarScannerStarter implements AnalysisAbortingListener {

    private static final Logger LOGGER = Logger.getInstance(SonarScannerStarter.class);

    @NotNull
    private final Project project;

    @NotNull
    private final String title;

    private final boolean startProgressInBackground;

    private final boolean startProgressModal;

    private final AtomicBoolean cancellingByUser;



    public SonarScannerStarter(
            @NotNull final Project project,
            @NotNull final String title) {
        this(project, title, ProgressStartType.RunInBackground);
    }

    public SonarScannerStarter(
            @NotNull final Project project,
            @NotNull final String title,
            @NotNull final ProgressStartType progressStartType) {
        this.project = project;
        this.title = title;
        switch (progressStartType) {
            case RunInBackground:
                this.startProgressInBackground = true;
                this.startProgressModal = false;
                break;
            case Modal:
                this.startProgressInBackground = false;
                this.startProgressModal = true;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported " + progressStartType);
        }
        this.cancellingByUser = new AtomicBoolean();
        MessageBusManager.subscribe(project, this, AnalysisAbortingListener.TOPIC, this);
    }

    public final void start() {
        EventDispatchThreadHelper.checkEDT();
        ProblemCacheService.getInstance(project).setAnalyzeScope(createAnalyzeScope());
        MessageBusManager.publishAnalysisStarted(project);

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            final ToolWindow toolWindow = ToolWindowFactoryImpl.getWindow(project);
            if (toolWindow == null) {
                throw new IllegalStateException("No SonarAnalyzer ToolWindow");
            }
            /*
             * Important: Make sure the tool window is initialized.
             * This call is to important to make it just in case of false = toolWindowToFront
             * because we have no guarantee that activateToolWindow works.
             */
            ((ToolWindowImpl) toolWindow).ensureContentInitialized();
            ToolWindowFactoryImpl.showWindowContent(toolWindow, 1);
        }

        final CompilerManager compilerManager = CompilerManager.getInstance(project);
        createCompileScope(compilerManager, compileScope -> {
            if (compileScope != null) {
                MessageBusManager.publishLog(project, "Start build project [" + project.getName() + "]", LogOutput.Level.INFO);

                finalizeCompileScope(compileScope);
                compilerManager.make(compileScope, (aborted, errors, warnings, compileContext) -> {
                    try {
                        ToolWindowFactoryImpl.showWindowContent(ToolWindowFactoryImpl.getWindow(project), 1);
                        if (aborted) {
                            MessageBusManager.publishLog(project, "build aborted", LogOutput.Level.ERROR);
                        }
                        if (errors > 0) {
                            MessageBusManager.publishLog(project, "build failed", LogOutput.Level.ERROR);
                        }
                        if (aborted || errors > 0) {
                            MessageBusManager.publishAnalysisFinished(project, new Object(), null);
                        }
                        // TODO: 这里原先是要判断了只有设置的是编译后不启动检测才会执行
                        // 猜测原因是有其他地方设置了编译后自动执行检测，因此防止触发两次
                        if (!aborted && errors == 0) {
                            EventDispatchThreadHelper.checkEDT(); // see javadoc of CompileStatusNotification
                            // Compiler can cause dumb mode, and finished() is invoked inside.
                            // We need to continue outside dumb mode to make activateToolWindow work f. e.
                            DumbService.getInstance(project).runWhenSmart(() -> {
                                EventDispatchThreadHelper.checkEDT();
                                startImpl(true);
                            });
                        }
                    } catch (Exception e) {
                        MessageBusManager.publishAnalysisFinished(project, new Object(), e);
                    }
                });
            }
        });
    }

    @Override
    public void analysisAborting() {
        this.cancellingByUser.set(true);
    }

    protected abstract void createCompileScope(
            @NotNull final CompilerManager compilerManager,
            @NotNull final Consumer<CompileScope> consumer);

    protected abstract AnalyzeScope createAnalyzeScope();


//    protected final void showWarning(@NotNull final String message) {
//        EventDispatchThreadHelper.invokeLater(() -> BalloonTipFactory.showToolWindowWarnNotifier(
//                project, message + " " + ResourcesLoader.getString("analysis.aborted")));
//    }

    /**
     * Depend on selected configuration is creepy but no other way to make it work with Android, see
     * https://intellij-support.jetbrains.com/hc/en-us/community/posts/207521525-CompilerManager-make-does-not-all-tasks-in-case-of-a-Android-module-project
     * <p>
     * Maybe, sometime, we should make this more clever and search for the best configuration instead of just use the selected.
     */
    private void finalizeCompileScope(@NotNull final CompileScope compileScope) {
        final RunnerAndConfigurationSettings runnerAndConfigurationSettings = RunManager.getInstance(project).getSelectedConfiguration();
        if (runnerAndConfigurationSettings != null) {
            final RunConfiguration runConfiguration = runnerAndConfigurationSettings.getConfiguration();
            compileScope.putUserData(CompilerManager.RUN_CONFIGURATION_KEY, runConfiguration);
            compileScope.putUserData(CompilerManager.RUN_CONFIGURATION_TYPE_ID_KEY, runConfiguration.getType().getId());
        }
    }

    private void startImpl(final boolean justCompiled) {
        final Task task;
        if (startProgressModal) {
            task = new Task.Modal(project, title, false) {
                @Override
                public void run(@NotNull final ProgressIndicator indicator) {
                    asyncStart(indicator, justCompiled);
                }
            };
        } else {
            task = new Task.Backgroundable(project, title, false) {
                @Override
                public void run(@NotNull final ProgressIndicator indicator) {
                    asyncStart(indicator, justCompiled);
                }

                @Override
                public boolean shouldStartInBackground() {
                    return startProgressInBackground;
                }
            };
        }
        task.queue();
    }

    private void asyncStart(@NotNull final ProgressIndicator indicator, final boolean justCompiled) {
        indicator.setIndeterminate(true);
//        indicator.setText("对项目[" + project.getName() + "]执行Sonar代码检测");
        try {
            if (!configCompleted()) {
                throw new ConfigException(ResourcesLoader.getString("error.analysis.noConnection"));
            }
            asyncStartImpl(indicator, justCompiled);
        } catch (Exception exc) {
            // SonarScanner内部错误在log中记录的准确，虽然每个error log会中断检查过程并抛出ScannerException但message变得不直接，因此在log中弹出错误提示而此处忽略
            // 修改：log中的有些error并不会阻断整个分析过程，因此原先此处的处理就显得不合理，因此在log中不再提示，均在catch中提示失败
            // if (!(exc instanceof ScannerException)) {
            //     EventDispatchThreadHelper.invokeLater(() -> {
            //         BalloonTipFactory.showToolWindowErrorNotifier(project, createErrorInfo(exc.getMessage()).toString());
            //         MessageBusManager.publishLog(project, exc.getMessage(), LogOutput.Level.ERROR);
            //     });
            // }
            EventDispatchThreadHelper.invokeLater(() -> {
                BalloonTipFactory.showToolWindowErrorNotifier(project, createErrorInfo(exc.getMessage()).toString());
                String logMessage = exc instanceof ConfigException || exc instanceof ScannerException ? exc.getMessage() : LogUtils.formatException(exc);
                MessageBusManager.publishLog(project, logMessage, LogOutput.Level.ERROR);
            });
        } finally {
            MessageBusManager.publishAnalysisFinishedToEDT(project, new Object(), null);
        }
    }

    private void asyncStartImpl(@NotNull final ProgressIndicator indicator, final boolean justCompiled) {
        LogOutput logOutput = new LogOutputImpl(project);
        EmbeddedScannerHelper.startEmbeddedScanner(project, createAnalyzeScope(), logOutput);
    }

    private boolean configCompleted() {
        return SettingsUtils.getSonarQubeConnection(project) != null;
    }

    public static StringBuilder createErrorInfo(String errorMessage) {
        final StringBuilder ret = new StringBuilder();
//        ret.append("<h2>").append("Sonar analysis failed").append("</h2>");
//        ret.append("<p>").append(errorMessage).append("</p>");
//        ret.append("<br>");
//        ret.append("Go to Log for more details");
//        ret.append("<br>");
        ret.append(ResourcesLoader.getString("analysis.failed.message", errorMessage));
        return ret;
    }

    public static StringBuilder createSuccessInfo() {
        final StringBuilder ret = new StringBuilder();
        ret.append(ResourcesLoader.getString("analysis.success.message"));
        return ret;
    }

    public static StringBuilder createReportDisplayErrorInfo() {
        final StringBuilder ret = new StringBuilder();
        ret.append(ResourcesLoader.getString("analysis.display.failed.message"));
        return ret;
    }

}
