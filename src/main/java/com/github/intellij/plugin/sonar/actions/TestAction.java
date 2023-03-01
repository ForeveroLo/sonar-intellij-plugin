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

package com.github.intellij.plugin.sonar.actions;

import com.github.intellij.plugin.sonar.messages.MessageBusManager;
import com.github.intellij.plugin.sonar.service.ProblemCacheService;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.github.intellij.plugin.sonar.core.AnalyzeState;
import com.github.intellij.plugin.sonar.core.Report;
import com.github.intellij.plugin.sonar.core.ReportUtils;
import com.github.intellij.plugin.sonar.common.EventDispatchThreadHelper;
import org.jetbrains.annotations.NotNull;

public class TestAction extends AbstractAction {
    @Override
    public void updateImpl(
            @NotNull AnActionEvent e,
            @NotNull Project project,
            @NotNull ToolWindow toolWindow,
            @NotNull AnalyzeState state) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setVisible(false);
    }


    @Override
    public void actionPerformedImpl(
            @NotNull AnActionEvent e,
            @NotNull Project project,
            @NotNull ToolWindow toolWindow,
            @NotNull AnalyzeState state) {
//        System.out.println(project.getBasePath());
//        System.out.println(project.getWorkspaceFile().getPath());
//        String fullClassPath = OrderEnumerator.orderEntries(project).recursively().getPathsList().getPathsString();
//        Arrays.stream(fullClassPath.split(";")).forEach(n -> {
//            System.out.println(n);
//        });
//
//        Module[] modules = ModuleManager.getInstance(project).getModules();
//        for (Module module : modules) {
//            CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
//            VirtualFile compilerOutPath = compilerModuleExtension.getCompilerOutputPath();
//            if (compilerOutPath != null) {
//                System.out.println(module.getName());
//                System.out.println(compilerOutPath.getCanonicalPath());
//            }
//            ModuleRootManager root = ModuleRootManager.getInstance(module);
//            String[] rootUrls = root.getSourceRootUrls(false);
//            for (String rootUrl : rootUrls) {
//                System.out.println(rootUrl);
//            }
//        }

//        Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
//        String version = sdk.getVersionString();
//
//
        Thread thread = new Thread(() -> {
            Report report = ReportUtils.createReport(e.getProject());
            ProblemCacheService problemCacheService = ProblemCacheService.getInstance(project);
            problemCacheService.setIssues(report.getIssues());
            problemCacheService.setStats(
                    report.getBugCount(),
                    report.getCodeSmellCount(),
                    report.getVulnerabilityCount(),
                    report.getDuplicatedBlocksCount(),
                    report.getSecurityHotSpotCount());
            EventDispatchThreadHelper.invokeLater(() -> {
                DaemonCodeAnalyzer.getInstance(project).restart();
                MessageBusManager.publishAnalysisFinished(e.getProject(), new Object(), null);
            });
        });
        thread.start();

//        NotificationGroup.balloonGroup("Sonar Intellij plugin Balloon Notification").createNotification(
//                "Sonar Intellij plugin",
//                ResourcesLoader.getString("settings.uiLanguages.switchSuccess"),
//                NotificationType.INFORMATION,
//                new NotificationListener.Adapter() {
//                    @Override
//                    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
//                        ApplicationManagerEx.getApplicationEx().restart(false);
//                    }
//                }).notify(e.getProject());

        // System.out.println(IdeaUtils.getFullClassPath(e.getProject()));

//        GitService.getInstance(project).getChangedFiles().forEach(n -> {
//            System.out.println(n.getVirtualFile().getCanonicalPath());
//        });
    }
}
