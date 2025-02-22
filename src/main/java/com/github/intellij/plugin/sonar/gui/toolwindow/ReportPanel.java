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

package com.github.intellij.plugin.sonar.gui.toolwindow;

import java.awt.BorderLayout;
import java.awt.CardLayout;

import com.github.intellij.plugin.sonar.common.EventDispatchThreadHelper;
import com.github.intellij.plugin.sonar.common.LogUtils;
import com.github.intellij.plugin.sonar.core.SonarScannerStarter;
import com.github.intellij.plugin.sonar.extensions.ToolWindowFactoryImpl;
import com.github.intellij.plugin.sonar.gui.common.BalloonTipFactory;
import com.github.intellij.plugin.sonar.gui.common.UIUtils;
import com.github.intellij.plugin.sonar.messages.AnalysisStateListener;
import com.github.intellij.plugin.sonar.messages.ClearListener;
import com.github.intellij.plugin.sonar.messages.MessageBusManager;
import com.github.intellij.plugin.sonar.resources.ResourcesLoader;
import com.github.intellij.plugin.sonar.service.ProblemCacheService;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.scanner.api.LogOutput;

public class ReportPanel extends JBPanel implements AnalysisStateListener, ClearListener {
    private Project project;

    private LeftToolbarPanel leftToolbarPanel;
    private JBPanel bodyPanel;
    private CardLayout bodyPanelLayout;

    private IssuesPanel issuesPanel;
    private IssueDetailPanel issueDetailPanel;

    public ReportPanel(@NotNull Project project) {
        this.project = project;
        setLayout(new BorderLayout());
        init();
        MessageBusManager.subscribeAnalysisState(project, this, this);
        MessageBusManager.subscribe(project, this, ClearListener.TOPIC, this::clear);
    }

    private void init() {
        leftToolbarPanel = new LeftToolbarPanel();
        add(leftToolbarPanel, BorderLayout.WEST);

        bodyPanel = new JBPanel();
        bodyPanelLayout = new CardLayout();
        bodyPanel.setLayout(bodyPanelLayout);
        add(bodyPanel, BorderLayout.CENTER);

        bodyPanel.add("EMPTY", new MessagePanel(ResourcesLoader.getString("toolWindow.report.emptyText")));

        OnePixelSplitter listAndCurrentSplitter = new OnePixelSplitter();
        listAndCurrentSplitter.getDivider().setBackground(UIUtils.borderColor());

        issuesPanel = new IssuesPanel(project);
        listAndCurrentSplitter.setFirstComponent(issuesPanel);

        issueDetailPanel = new IssueDetailPanel(project);
        listAndCurrentSplitter.setSecondComponent(issueDetailPanel);

        listAndCurrentSplitter.setProportion(0.35f);
        bodyPanel.add("REPORT", listAndCurrentSplitter);
        bodyPanelLayout.show(bodyPanel, "EMPTY");
    }

    public void refresh() {
        issuesPanel.refresh();
    }

    public void reset() {
        bodyPanelLayout.show(bodyPanel, "EMPTY");
        issuesPanel.reset();
        issueDetailPanel.reset();
    }

    @Override
    public void analysisAborted() {

    }

    @Override
    public void analysisAborting() {

    }

    @Override
    public void analysisFinished(@NotNull Object result, @Nullable Throwable error) {
        // 只有成功分析且成功解析分析报告才展示
        if (ProblemCacheService.getInstance(project).isInitialized()) {
            EventDispatchThreadHelper.invokeLater(() -> {
                try {
                    refresh();
                    bodyPanelLayout.show(bodyPanel, "REPORT");
                    DaemonCodeAnalyzer.getInstance(project).restart();
                    ToolWindowFactoryImpl.showWindowContent(ToolWindowFactoryImpl.getWindow(project), 0);
                    BalloonTipFactory.showToolWindowInfoNotifier(project, SonarScannerStarter.createSuccessInfo().toString());
                } catch (Exception e) {
                    BalloonTipFactory.showToolWindowErrorNotifier(project, SonarScannerStarter.createReportDisplayErrorInfo().toString());
                    MessageBusManager.publishLog(project, ResourcesLoader.getString("analysis.display.failed.message"), LogOutput.Level.ERROR);
                    MessageBusManager.publishLog(project, LogUtils.formatException(e), LogOutput.Level.ERROR);
                }
            });
        }
    }

    @Override
    public void analysisStarted() {
        reset();
    }

    @Override
    public void clear() {
        reset();
    }
}
