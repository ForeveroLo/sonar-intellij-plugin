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

package com.github.intellij.plugin.sonar.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.BooleanFunction;
import com.intellij.util.ui.JBUI;
import com.github.intellij.plugin.sonar.config.WorkspaceSettings;
import com.github.intellij.plugin.sonar.gui.common.UIUtils;
import com.github.intellij.plugin.sonar.gui.error.ErrorPainter;
import icons.PluginIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SettingsDialog extends DialogWrapper {
    private Project project;
    private ErrorPainter errorPainter;
    private JBTextField sonarHostUrlField;
    private JBTextField sonarTokenField;

    public SettingsDialog(@Nullable Project project) {
        super(true);
        this.project = project;

        init();
        setTitle("SonarAnalyzer Settings");
        setResizable(false);
        getContentPanel().setBorder(JBUI.Borders.empty());
        ((JComponent)getContentPanel().getComponent(1)).setBorder(JBUI.Borders.empty(0, 12, 8, 12));
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBPanel content = new JBPanel();
        content.setPreferredSize(new Dimension(440, content.getPreferredSize().height));
        content.setBorder(JBUI.Borders.empty(20, 20));
        content.setLayout(new BorderLayout());

        JBLabel sonarQubeLogoLabel = new JBLabel(PluginIcons.SONAR_QUBE);
        sonarQubeLogoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(UIUtils.wrappedInBorderLayoutPanel(sonarQubeLogoLabel, BorderLayout.CENTER), BorderLayout.NORTH);

        errorPainter = new ErrorPainter();
        errorPainter.installOn((JPanel) getContentPanel(), () -> {
        });

        JBPanel formPanel = new JBPanel(new GridBagLayout());
        formPanel.setBorder(JBUI.Borders.empty(20, 0, 20, 0));
        GridBagConstraints c = new GridBagConstraints();

        WorkspaceSettings workspaceSettings = WorkspaceSettings.getInstance();
        sonarHostUrlField = createFiled(workspaceSettings.getSonarHostUrl(), "Example: http://localhost:9000");
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(new JBLabel("URL: "), c);
        c.gridx = 1;
        c.weightx = 1;
        formPanel.add(sonarHostUrlField, c);

        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.insets = new Insets(5, 0, 0, 0);
        sonarTokenField = createFiled(workspaceSettings.getSonarToken(), "");
        formPanel.add(new JBLabel("Token: "), c);

        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(5, 0, 0, 0);
        formPanel.add(sonarTokenField, c);

        content.add(formPanel, BorderLayout.CENTER);

        return content;
    }

    private JBTextField createFiled(String defaultText, String emptyText) {
        JBTextField textField = new JBTextField(defaultText);
        textField.getEmptyText().setText(emptyText);
        BooleanFunction<JBTextField> statusVisibleFunction = jbTextField -> StringUtil.isEmptyOrSpaces(textField.getText());
        textField.putClientProperty("StatusVisibleFunction", statusVisibleFunction);
        textField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                errorPainter.setValid(textField, !StringUtil.isEmptyOrSpaces(textField.getText()));
            }
        });
        errorPainter.setValid(textField, !StringUtil.isEmptyOrSpaces(textField.getText()));
        return textField;
    }


    @NotNull
    @Override
    protected Action[] createActions() {
        DialogWrapper that = this;
        Action saveAction = new AbstractAction("OK") {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
            }
        };
        Action closeAction = new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                that.close(0);
            }
        };
        return new Action[] { saveAction, closeAction };
    }

    private void save() {
        String sonarHostUrl = sonarHostUrlField.getText();
        if (StringUtil.isEmptyOrSpaces(sonarHostUrl)) {
            return;
        }

        String sonarToken = sonarTokenField.getText();
        if (StringUtil.isEmptyOrSpaces(sonarToken)) {
            return;
        }

        WorkspaceSettings workspaceSettings = WorkspaceSettings.getInstance();
        workspaceSettings.sonarHostUrl = sonarHostUrl;
        workspaceSettings.sonarToken = sonarToken;

        close(0);
    }

}
