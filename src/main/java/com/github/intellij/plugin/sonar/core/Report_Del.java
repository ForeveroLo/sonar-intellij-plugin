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

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.github.intellij.plugin.sonar.api.RulesSearchResponse;
import com.github.intellij.plugin.sonar.api.SonarApiImpl;
import com.github.intellij.plugin.sonar.common.IdeaUtils;
import com.github.intellij.plugin.sonar.common.exceptions.ApiRequestFailedException;
import com.github.intellij.plugin.sonar.config.WorkspaceSettings;
import com.github.intellij.plugin.sonar.messages.MessageBusManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.sonar.core.util.CloseableIterator;
import org.sonar.scanner.protocol.output.ScannerReport;
import org.sonar.scanner.protocol.output.ScannerReportReader;
import org.sonarsource.scanner.api.LogOutput;

public class Report_Del {
    private Project project;
    private File reportDir;
    private int bugCount;
    private int codeSmellCount;
    private int vulnerabilityCount;
    private int duplicatedBlocksCount;
    private ConcurrentMap<PsiFile, List<AbstractIssue>> issues;

    public Report_Del(@NotNull Project project, @NotNull File reportDir) {
        this.project = project;
        this.reportDir = reportDir;
        issues = new ConcurrentHashMap<>();
        analyze();
    }

    public int getBugCount() {
        return bugCount;
    }

    public int getCodeSmellCount() {
        return codeSmellCount;
    }

    public int getVulnerabilityCount() {
        return vulnerabilityCount;
    }

    public int getDuplicatedBlocksCount() {
        return duplicatedBlocksCount;
    }

    public ConcurrentMap<PsiFile, List<AbstractIssue>> getIssues() {
        return issues;
    }

    private void analyze() {
        List<RulesSearchResponse.Rule> rules = getRules();
        List<Integer> componentFileNumbers = getAllComponentFileNumbers();
        ScannerReportReader reader = new ScannerReportReader(reportDir);
        for (Integer componentFileNumber : componentFileNumbers) {
            ScannerReport.Component component = reader.readComponent(componentFileNumber);
            CloseableIterator<ScannerReport.Issue> reportIssues = reader.readComponentIssues(componentFileNumber);
            CloseableIterator<ScannerReport.Duplication> reportDuplications = reader.readComponentDuplications(componentFileNumber);

            String projectRelativePath;
            File file;
            PsiFile psiFile;

            if (reportIssues.hasNext() || reportDuplications.hasNext()) {
                projectRelativePath = component.getProjectRelativePath();
                file = Paths.get(project.getBasePath(), projectRelativePath).toFile();
                psiFile = IdeaUtils.getPsiFile(project, file);

                if (!issues.containsKey(psiFile)) {
                    issues.put(psiFile, new ArrayList<>());
                }
            } else {
                continue;
            }

            while (reportIssues.hasNext()) {
                ScannerReport.Issue reportIssue = reportIssues.next();
                String issueRuleKey = String.format("%s:%s", reportIssue.getRuleRepository(), reportIssue.getRuleKey());
                RulesSearchResponse.Rule rule = findRule(rules, issueRuleKey);

                boolean ignoreIssue = false;
                switch (rule.getType()) {
                    case "BUG":
                        bugCount++;
                        break;
                    case "VULNERABILITY":
                        vulnerabilityCount++;
                        break;
                    case "CODE_SMELL":
                        codeSmellCount++;
                        break;
                    default:
                        ignoreIssue = true;
                        break;
                }

                if (ignoreIssue) {
                    MessageBusManager.publishLog(project, String.format("Rule[%s] type[%s] is not supported", issueRuleKey, rule.getType()), LogOutput.Level.ERROR);
                    continue;
                }

                Issue issue = new Issue(
                        psiFile,
                        reportIssue.getRuleRepository(),
                        reportIssue.getRuleKey(),
                        reportIssue.getMsg(),
                        reportIssue.getSeverity().toString(),
                        reportIssue.getTextRange().getStartLine(),
                        reportIssue.getTextRange().getEndLine(),
                        reportIssue.getTextRange().getStartOffset(),
                        reportIssue.getTextRange().getEndOffset(),
                        rule.getType(),
                        rule.getName(),
                        rule.getHtmlDesc());
                issues.get(psiFile).add(issue);
            }

            while (reportDuplications.hasNext()) {
                String issueRuleKey = String.format("%s:%s", "common-java", "DuplicatedBlocks");
                RulesSearchResponse.Rule rule = findRule(rules, issueRuleKey);

                AbstractIssue issue = issues.get(psiFile).stream().filter(n -> n.ruleKey.equalsIgnoreCase("DuplicatedBlocks")).findFirst().orElse(null);
                if (issue == null) {
                    issue = new DuplicatedBlocksIssue_Del(
                            psiFile,
                            "common-java",
                            "DuplicatedBlocks",
                            rule.getSeverity(),
                            rule.getType(),
                            rule.getName(),
                            rule.getHtmlDesc()
                    );
                    issues.get(psiFile).add(issue);
                    codeSmellCount++;
                }

                DuplicatedBlocksIssue_Del duplicatedBlocksIssue = (DuplicatedBlocksIssue_Del)issue;
                ScannerReport.Duplication duplication = reportDuplications.next();
                DuplicatedBlocksIssue_Del.Block block = new DuplicatedBlocksIssue_Del.Block(duplication.getOriginPosition().getStartLine(), duplication.getOriginPosition().getEndLine());
                duplicatedBlocksIssue.addBlock(block);
                duplicatedBlocksCount++;

                boolean existDuplicateInSameFile = false;
                for (ScannerReport.Duplicate d : duplication.getDuplicateList()) {
                    DuplicatedBlocksIssue_Del.Duplicate duplicate = new DuplicatedBlocksIssue_Del.Duplicate(
                            d.getOtherFileRef() == 0 ? "" : reader.readComponent(d.getOtherFileRef()).getProjectRelativePath(),
                            d.getRange().getStartLine(),
                            d.getRange().getEndLine()
                    );
                    block.addDuplicate(duplicate);
                    boolean duplicateInSameFile = d.getOtherFileRef() == 0;
                    if (duplicateInSameFile) {
                        existDuplicateInSameFile = true;
                    }
                }

                if (existDuplicateInSameFile) {
                    DuplicatedBlocksIssue_Del.Duplicate duplicateUseCurrentBlock = new DuplicatedBlocksIssue_Del.Duplicate(
                            "",
                            block.getLineStart(),
                            block.getLineEnd()
                    );
                    block.getDuplicates().forEach(d -> {
                        if (StringUtil.isEmpty(d.getPath())) {
                            DuplicatedBlocksIssue_Del.Block additionalBlock = new DuplicatedBlocksIssue_Del.Block(d.getStartLine(), d.getEndLine());
                            additionalBlock.addDuplicate(duplicateUseCurrentBlock);
                            List<DuplicatedBlocksIssue_Del.Duplicate> otherDuplicates = block.getDuplicates().stream()
                                    .filter(n -> !StringUtil.isEmpty(n.getPath()) || n.getStartLine() != d.getStartLine() || n.getEndLine() != d.getEndLine())
                                    .collect(Collectors.toList());
                            additionalBlock.addDuplicates(otherDuplicates);
                            duplicatedBlocksIssue.getBlocks().add(additionalBlock);
                            duplicatedBlocksCount++;
                        }
                    });
                }
            }
        }
    }

    private List<Integer> getAllComponentFileNumbers() {
        List<Integer> componentFileNumbers = new ArrayList<>();
        reportDir.listFiles((dir, name) -> {
            if (name.startsWith("component-")) {
                componentFileNumbers.add(Integer.parseInt(name.split("-")[1].replace(".pb", "")));
                return true;
            }
            return false;
        });
        return componentFileNumbers;
    }

    private List<RulesSearchResponse.Rule> getRules() {
        try {
            List<String> languages = WorkspaceSettings.getInstance().languages;
            List<RulesSearchResponse.Rule> rules = new SonarApiImpl(project).getRules(languages);
            return rules;
        } catch (ApiRequestFailedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private @NotNull RulesSearchResponse.Rule findRule(List<RulesSearchResponse.Rule> rules, String ruleKey) {
        RulesSearchResponse.Rule rule = rules.stream().filter(n -> n.getKey().equalsIgnoreCase(ruleKey)).findFirst().orElse(null);
        if (rule == null) {
            throw new RuntimeException(String.format("No rule named [%s] was found", ruleKey));
        }
        return rule;
    }
}
