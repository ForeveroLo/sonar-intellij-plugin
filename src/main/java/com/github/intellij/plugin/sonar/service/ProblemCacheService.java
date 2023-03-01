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

package com.github.intellij.plugin.sonar.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.github.intellij.plugin.sonar.core.AbstractIssue;
import com.github.intellij.plugin.sonar.core.AnalyzeScope;
import com.github.intellij.plugin.sonar.core.DuplicatedBlocksIssue;
import com.github.intellij.plugin.sonar.core.Issue;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class ProblemCacheService {
    private Project project;

    private boolean initialized = false;
    private ConcurrentMap<PsiFile, List<AbstractIssue>> issues;
    private int bugCount;
    private int codeSmellCount;
    private int vulnerabilityCount;
    private int duplicatedBlocksCount;
    private int securityHotSpotCount;

    private int blockerCount,  criticalCount,  majorCount,  minorCount,  infoCount;

    private CopyOnWriteArraySet<String> profileLanguages;
    private CopyOnWriteArraySet<String> ignoreRules;
    private int ignoreIssueCount;

    private Set<String> filters;

    private AnalyzeScope analyzeScope;

    public ProblemCacheService(Project project) {
        this.project = project;
        issues = new ConcurrentHashMap<>();
        bugCount = 0;
        codeSmellCount = 0;
        vulnerabilityCount = 0;
        duplicatedBlocksCount = 0;
        securityHotSpotCount = 0;

        profileLanguages = new CopyOnWriteArraySet<>();
        ignoreRules = new CopyOnWriteArraySet<>();
        ignoreIssueCount = 0;

        filters = new HashSet<>();
    }

    public ConcurrentMap<PsiFile, List<AbstractIssue>> getIssues() {
        return issues;
    }

    public ConcurrentMap<PsiFile, List<AbstractIssue>> getFilteredIssues() {
        if (filters.size() == 0) {
            return issues;
        }

        boolean includeBug = filters.contains("BUG");
        boolean includeCodeSmell = filters.contains("CODE_SMELL");
        boolean includeVulnerability = filters.contains("VULNERABILITY");
        boolean includeSecurityHotspot = filters.contains("SECURITY_HOTSPOT");
        boolean includeDuplication = filters.contains("DUPLICATION");
        boolean filterByType = includeBug || includeCodeSmell || includeVulnerability || includeSecurityHotspot || includeDuplication;
        boolean includeUpdatedFiles = filters.contains("UPDATED_FILES");
        boolean includeNotUpdatedFiles = filters.contains("NOT_UPDATED_FILES");
        boolean filterByScope = includeUpdatedFiles || includeNotUpdatedFiles;
        boolean includeResolved = filters.contains("RESOLVED");
        boolean includeUnresolved = filters.contains("UNRESOLVED");
        boolean filterByStatus = includeResolved || includeUnresolved;
        boolean includeBlocker = filters.contains("BLOCKER");
        boolean includeCritical = filters.contains("CRITICAL");
        boolean includeMajor = filters.contains("MAJOR");
        boolean includeMinor = filters.contains("MINOR");
        boolean includeInfo = filters.contains("INFO");
        boolean filterBySeverity = includeBlocker || includeCritical || includeMajor || includeMinor || includeInfo;

        List<PsiFile> changedFiles = GitService.getInstance(project).getChangedFiles();

        ConcurrentMap<PsiFile, List<AbstractIssue>> ret = new ConcurrentHashMap<>();
        issues.forEach((psiFile, issues) -> {
            List<AbstractIssue> retIssues = new ArrayList<>();
            for (AbstractIssue issue : issues) {
                boolean include = true;

                if (filterByType) {
                    include = false;

                    if (includeBug && issue.getType().equals("BUG")) {
                        include = true;
                    }
                    if (includeCodeSmell && issue.getType().equals("CODE_SMELL")) {
                        include = true;
                    }
                    if (includeVulnerability && issue.getType().equals("VULNERABILITY")) {
                        include = true;
                    }
                    if (includeSecurityHotspot && issue.getType().equals("SECURITY_HOTSPOT")) {
                        include = true;
                    }
                    if (includeDuplication && issue instanceof DuplicatedBlocksIssue) {
                        include = true;
                    }

                    if (!include) {
                        continue;
                    }
                }

                if (filterByScope) {
                    include = false;

                    if (includeUpdatedFiles && changedFiles.contains(issue.getPsiFile())) {
                        include = true;
                    }
                    if (includeNotUpdatedFiles && !changedFiles.contains(issue.getPsiFile())) {
                        include = true;
                    }

                    if (!include) {
                        continue;
                    }
                }

                if (filterByStatus) {
                    include = false;

                    if (includeResolved && issue.isFixed()) {
                        include = true;
                    }
                    if (includeUnresolved && !issue.isFixed()) {
                        include = true;
                    }

                    if (!include) {
                        continue;
                    }
                }

                if (filterBySeverity) {
                    if(includeBlocker && StringUtils.equalsIgnoreCase(issue.getSeverity(),"BLOCKER")){
                        include = true;
                    } else if(includeCritical && StringUtils.equalsIgnoreCase(issue.getSeverity(),"CRITICAL")){
                        include = true;
                    } else if(includeMajor && StringUtils.equalsIgnoreCase(issue.getSeverity(),"MAJOR")){
                        include = true;
                    } else if(includeMinor && StringUtils.equalsIgnoreCase(issue.getSeverity(),"MINOR")){
                        include = true;
                    } else if(includeInfo && StringUtils.equalsIgnoreCase(issue.getSeverity(),"INFO")){
                        include = true;
                    }else {
                        include = false;
                    }
                }

                if (include) {
                    retIssues.add(issue);
                }

            }
            if (retIssues.size() > 0) {
                ret.put(psiFile, retIssues);
            }
        });

        return ret;
    }

    public void setIssues(ConcurrentMap<PsiFile, List<AbstractIssue>> issues) {
        issues.forEach(((psiFile, issueList) -> {
            if (issueList.size() > 0) {
                this.issues.put(psiFile, issueList);
            }
        }));
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

    public int getSecurityHotSpotCount() {
        return securityHotSpotCount;
    }

    public CopyOnWriteArraySet<String> getProfileLanguages() {
        return profileLanguages;
    }

    public CopyOnWriteArraySet<String> getIgnoreRules() {
        return ignoreRules;
    }

    public int getIgnoreIssueCount() {
        return ignoreIssueCount;
    }

    public int getBlockerCount() {
        return blockerCount;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public int getMajorCount() {
        return majorCount;
    }

    public int getMinorCount() {
        return minorCount;
    }

    public int getInfoCount() {
        return infoCount;
    }

    public void setStats(int bugCount, int codeSmellCount, int vulnerabilityCount, int duplicatedBlocksCount, int securityHotSpotCount) {
        initialized = true;
        this.bugCount = bugCount;
        this.codeSmellCount = codeSmellCount;
        this.vulnerabilityCount = vulnerabilityCount;
        this.duplicatedBlocksCount = duplicatedBlocksCount;
        this.securityHotSpotCount = securityHotSpotCount;
    }

    public void setSeverityStats(int blockerCount, int criticalCount, int majorCount, int minorCount, int infoCount) {
        initialized = true;
        this.blockerCount = blockerCount;
        this.criticalCount = criticalCount;
        this.majorCount = majorCount;
        this.minorCount = minorCount;
        this.infoCount = infoCount;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setAnalyzeScope(AnalyzeScope analyzeScope) {
        this.analyzeScope = analyzeScope;
    }

    public AnalyzeScope getAnalyzeScope() {
        return analyzeScope;
    }

    public void reset() {
        initialized = false;
        issues.clear();
        bugCount = 0;
        codeSmellCount = 0;
        vulnerabilityCount = 0;
        duplicatedBlocksCount = 0;
        securityHotSpotCount = 0;

        this.blockerCount = 0;
        this.criticalCount = 0;
        this.majorCount = 0;
        this.minorCount = 0;
        this.infoCount = 0;

        profileLanguages.clear();
        ignoreRules.clear();
        ignoreIssueCount = 0;

        filters.clear();
    }

    public int getUpdatedFilesIssueCount() {
        List<PsiFile> changedFiles = GitService.getInstance(project).getChangedFiles();
        int count = 0;
        for (Map.Entry<PsiFile, List<AbstractIssue>> entry : issues.entrySet()) {
            PsiFile psiFile = entry.getKey();
            List<AbstractIssue> issueList = entry.getValue();
            if (changedFiles.contains(psiFile)) {
                count += issueList.stream().filter(n -> n instanceof Issue).count();
                count += issueList.stream().anyMatch(n -> n instanceof DuplicatedBlocksIssue) ? 1 : 0;
            }
        }
        return count;
    }

    public int getNotUpdatedFilesIssueCount() {
        return issueTotalCount() - getUpdatedFilesIssueCount();
    }

    public int getFixedIssueCount() {
        int count = 0;
        for (Map.Entry<PsiFile, List<AbstractIssue>> entry : issues.entrySet()) {
            List<AbstractIssue> issueList = entry.getValue();
            int normalIssueFixedCount = (int)issueList.stream().filter(n -> n instanceof Issue && n.isFixed()).count();
            boolean duplicationIssueFixed = issueList.stream().filter(n -> n instanceof DuplicatedBlocksIssue && n.isFixed()).count() > 0;
            count += normalIssueFixedCount + (duplicationIssueFixed ? 1 : 0);
        }
        return count;
    }

    public int getUnresolvedIssueCount() {
        return issueTotalCount() - getFixedIssueCount();
    }

    public int issueTotalCount() {
        return bugCount + codeSmellCount + vulnerabilityCount + securityHotSpotCount;
    }

    public int severityTotalCount() {
        return blockerCount + criticalCount + majorCount + minorCount + infoCount;
    }

    public Set<String> getFilters() {
        return filters;
    }

    public static ProblemCacheService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, ProblemCacheService.class);
    }
}
