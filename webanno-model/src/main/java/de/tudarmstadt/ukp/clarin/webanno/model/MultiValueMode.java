/*
 * Copyright 2015
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
package de.tudarmstadt.ukp.clarin.webanno.model;

import de.tudarmstadt.ukp.clarin.webanno.support.PersistentEnum;

/**
 * Type of link features.
 */
public enum MultiValueMode
    implements PersistentEnum
{
    NONE("none"), ARRAY("array");

    private final String id;

    MultiValueMode(String aId)
    {
        id = aId;
    }

    @Override
    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return getId();
    }

    @Override
    public String toString()
    {
        return getId();
    }
}
