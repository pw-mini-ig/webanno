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
package de.tudarmstadt.ukp.clarin.webanno.constraints.model;

import static de.tudarmstadt.ukp.clarin.webanno.constraints.grammar.ConstraintsParser.asFlatString;

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import de.tudarmstadt.ukp.clarin.webanno.constraints.grammar.ASTRestriction;

/**
 * Class containing object representation of Restriction of a rule.
 */
public class Restriction
    implements Serializable
{
    private static final long serialVersionUID = -6950610587083804950L;

    private final String path;
    private final String value;
    private final boolean flagImportant;

    public Restriction(ASTRestriction aRestriction)
    {
        this(asFlatString(aRestriction.getPath()), aRestriction.getValue(),
                aRestriction.isImportant());
    }

    public Restriction(String aPath, String aValue)
    {
        this(aPath, aValue, false);
    }

    public Restriction(String aPath, String aValue, boolean aFlagImportant)
    {
        path = aPath;
        value = aValue;
        flagImportant = aFlagImportant;
    }

    public String getPath()
    {
        return path;
    }

    public String getValue()
    {
        return value;
    }

    public boolean isFlagImportant()
    {
        return flagImportant;
    }

    @Override
    public String toString()
    {
        return "Restriction [[" + path + "] = [" + value + "] important=" + flagImportant + "]";
    }

    @Override
    public boolean equals(final Object other)
    {
        if (!(other instanceof Restriction)) {
            return false;
        }
        Restriction castOther = (Restriction) other;
        return new EqualsBuilder().append(path, castOther.path).append(value, castOther.value)
                .append(flagImportant, castOther.flagImportant).isEquals();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder().append(path).append(value).append(flagImportant).toHashCode();
    }
}
