/*
 * Copyright 2020
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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer;

import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.enabledWhen;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.coloring.ColoringRulesConfigurationPanel;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.behaviors.OverlapModeSelect;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.behaviors.ValidationModeSelect;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;

public class RelationLayerTraitsEditor
    extends LayerTraitsEditor_ImplBase<RelationLayerTraits, RelationLayerSupport>
{
    private static final long serialVersionUID = -9082045435380184514L;

    public RelationLayerTraitsEditor(String aId, RelationLayerSupport aLayerSupport,
            IModel<AnnotationLayer> aLayer)
    {
        super(aId, aLayerSupport, aLayer);
    }

    @Override
    protected void initializeForm(Form<RelationLayerTraits> aForm)
    {
        aForm.add(new ValidationModeSelect("validationMode", getLayerModel()));

        OverlapModeSelect overlapMode = new OverlapModeSelect("overlapMode", getLayerModel());
        // Not configurable for layers that attach to tokens (currently that is the only layer on
        // which we use the attach feature)
        overlapMode.add(enabledWhen(() -> getLayerModelObject().getAttachFeature() == null));
        aForm.add(overlapMode);

        aForm.add(new ColoringRulesConfigurationPanel("coloringRules", getLayerModel(),
                getTraitsModel().bind("coloringRules.rules")));

        CheckBox crossSentence = new CheckBox("crossSentence");
        crossSentence.setOutputMarkupPlaceholderTag(true);
        crossSentence.setModel(PropertyModel.of(getLayerModel(), "crossSentence"));
        // Not configurable for layers that attach to tokens (currently that is the only layer on
        // which we use the attach feature)
        crossSentence.add(enabledWhen(() -> getLayerModelObject().getAttachFeature() == null));
        aForm.add(crossSentence);

        TextArea<String> onClickJavascriptAction = new TextArea<String>("onClickJavascriptAction");
        onClickJavascriptAction
                .setModel(PropertyModel.of(getLayerModel(), "onClickJavascriptAction"));
        onClickJavascriptAction.add(new AttributeModifier("placeholder",
                "alert($PARAM.PID + ' ' + $PARAM.PNAME + ' ' + $PARAM.DOCID + ' ' + "
                        + "$PARAM.DOCNAME + ' ' + $PARAM.fieldname);"));
        aForm.add(onClickJavascriptAction);
    }
}
