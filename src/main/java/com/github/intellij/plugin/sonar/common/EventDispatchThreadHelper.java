
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

package com.github.intellij.plugin.sonar.common;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public final class EventDispatchThreadHelper {

    private static final Logger LOGGER = Logger.getInstance(EventDispatchThreadHelper.class.getName());
    private static final boolean DEBUG_CHECK_EDT = true; // FIXME

    public static void invokeAndWait(@NotNull final Operation operation) {
        assert operation != null : "Operation must not be null!";
        if (EventQueue.isDispatchThread()) {
            operation.run();
            operation.onSuccess();
        } else {
            try {
                EventQueue.invokeAndWait(operation);
                operation.onSuccess();
            } catch (final Exception e) {
                operation.onFailure(e);
            }
        }
    }


    public static void checkNotEDT() {
        if (DEBUG_CHECK_EDT && EventQueue.isDispatchThread()) {
            throw new IllegalStateException();
        }
    }


    public static void checkEDT() {
        if (DEBUG_CHECK_EDT && !EventQueue.isDispatchThread()) {
            throw new IllegalStateException();
        }
    }


    public static void assertInEDTorADT() {
        if (!EventQueue.isDispatchThread() && !ApplicationManager.getApplication().isDispatchThread()) {
            final CallerStack caller = new CallerStack();
            final Throwable e = new NotInEDTViolation("Should run in EventDispatchThread or ApplcationtDispatchThread.");
            CallerStack.initCallerStack(e, caller);
            LOGGER.debug(e);
        }
    }


    public static void invokeLater(final Runnable runnable) {
        if (!EventQueue.isDispatchThread()) {
            @SuppressWarnings({"ThrowableInstanceNeverThrown"})
            final CallerStack caller = new CallerStack();
            final Runnable wrapper = () -> {
                try {
                    runnable.run();
                } catch (final RuntimeException | Error e) {
                    CallerStack.initCallerStack(e, caller);
                    throw e;
                }
            };
            EventQueue.invokeLater(wrapper);
        } else {
            // we are already in EventDispatcherThread.. Runnable can be called inline
            runnable.run();
        }
    }


    public interface Operation extends Runnable {

        void onFailure(Throwable failure);

        void onSuccess();

    }


    public abstract static class OperationAdapter implements Operation {

        public void onFailure(final Throwable failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            } else if (failure instanceof InvocationTargetException) {
                final Throwable target = ((InvocationTargetException) failure).getTargetException();
                final StackTraceElement[] stackTrace = target.getStackTrace();
                final StringBuilder message = new StringBuilder("Operation failed. ");
                if (stackTrace.length > 2) {
                    final StackTraceElement stackTraceElement = stackTrace[1];
                    message.append(stackTraceElement.getClassName()).append('.').append(stackTraceElement.getMethodName()).append("() - ");
                }
                message.append(target);
                LOGGER.error(message.toString(), target);
            }
        }

        public void onSuccess() {
            // stub
        }

    }


    private EventDispatchThreadHelper() {
        // utility
    }


    private static class NotInEDTViolation extends Exception {

        private static final long serialVersionUID = 1L;


        NotInEDTViolation(final String message) {
            super(message);
        }
    }


}
