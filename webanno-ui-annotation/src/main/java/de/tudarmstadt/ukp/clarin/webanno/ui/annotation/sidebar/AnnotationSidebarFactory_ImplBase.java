/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.core.Ordered;

public abstract class AnnotationSidebarFactory_ImplBase
    implements BeanNameAware, Ordered, AnnotationSidebarFactory
{
    private String beanName;

    @Override
    public void setBeanName(String aName)
    {
        beanName = aName;
    }

    @Override
    public String getBeanName()
    {
        return beanName;
    }

    @Override
    public int getOrder()
    {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
