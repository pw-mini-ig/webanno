/*
 * Copyright 2019
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
package de.tudarmstadt.ukp.clarin.webanno.agreement.measures.krippendorffalpha;

import de.tudarmstadt.ukp.clarin.webanno.agreement.measures.DefaultAgreementTraits;

public class KrippendorffAlphaAgreementTraits
    extends DefaultAgreementTraits
{
    private static final long serialVersionUID = -3843837791046164173L;

    private boolean excludeIncomplete;

    public boolean isExcludeIncomplete()
    {
        return excludeIncomplete;
    }

    public void setExcludeIncomplete(boolean aExcludeIncomplete)
    {
        excludeIncomplete = aExcludeIncomplete;
    }
}
