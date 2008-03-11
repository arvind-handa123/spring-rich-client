/*
 * Copyright 2002-2004 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.richclient.form.builder.support;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.springframework.binding.form.FormModel;
import org.springframework.richclient.control.MessageReportingOverlay;
import org.springframework.richclient.core.Severity;
import org.springframework.richclient.form.builder.FormComponentInterceptor;
import org.springframework.richclient.form.builder.FormComponentInterceptorFactory;
import org.springframework.richclient.util.OverlayHelper;

/**
 * Adds an "overlay" to a component that is triggered by a validation event. The overlaid image is retrieved by the
 * image key "severity.{severityShortCode}.overlay", where {severityShortCode} is the number returned by
 * {@link Severity#getShortCode()}. The image is placed at the bottom-left corner of the component, and the image's
 * tooltip is set to the validation message.
 * 
 * @author Oliver Hutchison
 * @author Mathias Broekelmann
 * @see OverlayHelper#attachOverlay
 */
public class OverlayValidationInterceptorFactory implements FormComponentInterceptorFactory {

    public OverlayValidationInterceptorFactory() {
    }

    public FormComponentInterceptor getInterceptor(FormModel formModel) {
        return new OverlayValidationInterceptor(formModel);
    }

    public class OverlayValidationInterceptor extends ValidationInterceptor {

        public OverlayValidationInterceptor(FormModel formModel) {
            super(formModel);
        }

        public void processComponent(String propertyName, final JComponent component) {
            final MessageReportingOverlay overlay = new ErrorReportingOverlay();

            registerGuarded(propertyName, overlay);
            registerMessageReceiver(propertyName, overlay);

            if (component.getParent() == null) {
                PropertyChangeListener waitUntilHasParentListener = new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent e) {
                        if (component.getParent() != null) {
                            component.removePropertyChangeListener("ancestor", this);
                            attachOverlay(overlay, component);
                        }
                    }
                };
                component.addPropertyChangeListener("ancestor", waitUntilHasParentListener);
            } else {
                attachOverlay(overlay, component);
            }
        }

        private void attachOverlay(MessageReportingOverlay overlay, JComponent component) {
            InterceptorOverlayHelper.attachOverlay(overlay, component, SwingConstants.SOUTH_WEST, 0, 0);
        }
    }

    /**
     * switches enabled state enabled will be false on validation error
     */
    private static class ErrorReportingOverlay extends MessageReportingOverlay {
        public void setEnabled(boolean enabled) {
            super.setEnabled(!enabled);
        }
    }
}