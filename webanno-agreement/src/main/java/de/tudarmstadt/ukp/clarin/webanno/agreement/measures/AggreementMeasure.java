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
package de.tudarmstadt.ukp.clarin.webanno.agreement.measures;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.uima.cas.CAS;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;

public interface AggreementMeasure<R extends Serializable>
{
    R getAgreement(Map<String, List<CAS>> aCasMap);

    AnnotationFeature getFeature();

    DefaultAgreementTraits getTraits();
}
