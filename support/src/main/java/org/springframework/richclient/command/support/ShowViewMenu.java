/*
 * Copyright 2002-2004 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.richclient.command.support;

import org.springframework.richclient.application.Application;
import org.springframework.richclient.application.ApplicationWindow;
import org.springframework.richclient.application.ViewDescriptor;
import org.springframework.richclient.application.config.ApplicationWindowAware;
import org.springframework.richclient.command.CommandGroup;

/**
 * @author Keith Donald
 */
public class ShowViewMenu extends CommandGroup implements ApplicationWindowAware {

    private static final String ID = "showViewMenu";

    private ApplicationWindow window;

    public ShowViewMenu() {
        super(ID);
    }

    public void setApplicationWindow(ApplicationWindow window) {
        this.window = window;
    }

    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        populate();
    }

    private void populate() {
        ViewDescriptor[] views = Application.services().getViewDescriptorRegistry().getViewDescriptors();
        for (int i = 0; i < views.length; i++) {
            ViewDescriptor view = views[i];
            addInternal(view.createShowViewCommand(window));
        }
    }
}