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

package com.github.intellij.plugin.sonar.messages;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.scanner.api.LogOutput;

public interface LogListener {
    Topic<LogListener> TOPIC = Topic.create("Sonar Analysis Log", LogListener.class);

    /**
     * Invoked by EDT.
     */
    void log(@NotNull final String formattedMessage, @NotNull LogOutput.Level level);
}
