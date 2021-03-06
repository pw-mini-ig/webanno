/*
 * Copyright 2020
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.event;

import org.apache.wicket.ajax.AjaxRequestTarget;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;

/**
 * Fired by {@link AnnotatorState} if the parameters controlling the viewport have changed.
 * <p>
 * NOTE: This event is not fired when basic configurations affecting the viewport are changed, e.g.
 * the {@link AnnotatorState#getPagingStrategy() paging strategy}. It is also not called when the
 * active document or active project changes.
 *
 * @see AnnotatorState#setPageBegin
 * @see AnnotatorState#setVisibleUnits
 * @see AnnotatorState#setFirstVisibleUnit
 * @see AnnotatorState#getFirstVisibleUnitIndex()
 * @see AnnotatorState#getLastVisibleUnitIndex()
 * @see AnnotatorState#getUnitCount()
 * @see AnnotatorState#getWindowBeginOffset()
 * @see AnnotatorState#getWindowEndOffset()
 */
public class AnnotatorViewportChangedEvent
{

    private final AjaxRequestTarget requestHandler;

    public AnnotatorViewportChangedEvent(AjaxRequestTarget aRequestHandler)
    {
        requestHandler = aRequestHandler;
    }

    public AjaxRequestTarget getRequestHandler()
    {
        return requestHandler;
    }
}
