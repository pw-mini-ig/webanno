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
package de.tudarmstadt.ukp.clarin.webanno.tsv;

import de.tudarmstadt.ukp.clarin.webanno.tsv.internal.tsv3x.Tsv3XCasDocumentBuilder;
import de.tudarmstadt.ukp.clarin.webanno.tsv.internal.tsv3x.Tsv3XCasSchemaAnalyzer;
import de.tudarmstadt.ukp.clarin.webanno.tsv.internal.tsv3x.Tsv3XSerializer;
import de.tudarmstadt.ukp.clarin.webanno.tsv.internal.tsv3x.model.*;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.dkpro.core.api.io.JCasFileWriter_ImplBase;
import org.dkpro.core.api.parameter.ComponentParameters;
import org.pw_mini_ig.exceptions.InvalidIGDefinitionException;
import org.pw_mini_ig.models.*;
import org.pw_mini_ig.tools.IgSchemaUtilities;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.io.IOUtils.buffer;

/**
 * Writes the yaml format (only for IG).
 * JCas objects are weird so we use the code from .tsv export to generate more 'readable' objects
 */
public class WebannoYamlWriter
    extends JCasFileWriter_ImplBase
{
    /**
     * The character encoding used by the input files.
     */
    public static final String PARAM_ENCODING = ComponentParameters.PARAM_TARGET_ENCODING;
    @ConfigurationParameter(name = PARAM_ENCODING, mandatory = true, defaultValue = "UTF-8")
    private String encoding;

    /**
     * Use this filename extension.
     */
    public static final String PARAM_FILENAME_EXTENSION = ComponentParameters.PARAM_FILENAME_EXTENSION;
    @ConfigurationParameter(name = PARAM_FILENAME_EXTENSION, mandatory = true, defaultValue = ".tsv")
    private String filenameSuffix;

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException
    {
        TsvSchema schema = Tsv3XCasSchemaAnalyzer.analyze(aJCas.getTypeSystem());
        TsvDocument doc = Tsv3XCasDocumentBuilder.of(schema, aJCas);

        class Node
        {
            public final int id; // -1 if not specified
            public final String name;
            public String text;
            public int beg; //span
            public int end; //span
            public List<Node> children = new ArrayList<>();

            public Node(int id, String name, String text) {
                this.id = id;
                this.name = name;
                this.text = text;
            }

            private SimpleNode simple() {
                return new SimpleNode(text, beg, end - beg);
            }

            public AbstractStatement createStatement(String statementName) throws InvalidIGDefinitionException {
                boolean isFact = statementName.equals("Fact/Observation");
                AtomicStatementType type = isFact ? AtomicStatementType.statementOfFact : AtomicStatementType.institutionalStatement;
                if (isFact) {
                    statementName = "Constitutive Statement";
                    for (Node c : children) {
                        if (c.name.equals("(A) Attribute")) {
                            statementName = "Regulative Statement";
                            break;
                        }
                    }
                }

                switch(statementName) {
                    case "Regulative Statement":
                        SimpleNode deontic = null;
                        List<ComponentWithoutProperties> aims = new ArrayList<>();
                        List<ComponentWithProperties> attributes = new ArrayList<>();
                        List<StatementOrComponentWithProperties> bdirs = new ArrayList<>();
                        List<StatementOrComponentWithProperties> binds = new ArrayList<>();
                        List<StatementOrComponentWithoutProperties> cacs = new ArrayList<>();
                        List<StatementOrComponentWithoutProperties> cexs = new ArrayList<>();
                        List<StatementOrComponentWithProperties> bdirProps = new ArrayList<>();
                        List<StatementOrComponentWithProperties> bindProps = new ArrayList<>();
                        List<StatementOrComponentWithProperties> attrProps = new ArrayList<>();

                        for (Node c : children) {
                            Statement nested = null;
                            if (c.name.equals("Regulative Statement") || c.name.equals("Constitutive Statement") || c.name.equals("Fact/Observation")) {
                                if (c.children.size() != 1) {
                                    // nested statement has to have an annotation
                                    return null;
                                }
                                nested = c.children.get(0).createStatement(c.name);
                                if (nested == null) {
                                    // nested error
                                    return null;
                                }
                            }
                            Node n = nested == null ? c : c.children.get(0);

                            switch (n.name) {
                                case "(A) Attribute":
                                    if (nested == null) {
                                        attributes.add(n.simple());
                                    }
                                    else {
                                        //cannot be a statement
                                        return null;
                                    }
                                    break;
                                case "(I) Aim":
                                    if (nested == null) {
                                        aims.add(n.simple());
                                    }
                                    else {
                                        //cannot be a statement
                                        return null;
                                    }
                                    break;
                                case "(Bdir) Object_Direct":
                                    bdirs.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(Bind) Object_Indirect":
                                    binds.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(D) Deontic":
                                    if (deontic != null) {
                                        //there can only be one deontic
                                        return null;
                                    }
                                    if (nested != null) {
                                        //cannot be a statement
                                        return null;
                                    }
                                    if (isFact) {
                                        //cannot be in a sof
                                        return null;
                                    }
                                    deontic = n.simple();
                                    break;
                                case "(Cac) Activation Condition":
                                    cacs.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(Cex) Execution Constraint":
                                    cexs.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(A, prop) Attribute_Property":
                                    attrProps.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(Bdir, prop) Object_Direct_Property":
                                    bdirProps.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(Bind, prop) Object_Indirect_Property":
                                    bindProps.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                default:
                                    //unexpected annotation type
                                    return null;
                            }
                        }
                        if (attributes.isEmpty()) {
                            return null;
                        }
                        if (aims.isEmpty()) {
                            return null;
                        }

                        // add properties
                        if (!attrProps.isEmpty()) {
                            ComponentWithProperties cp = new ComponentWithLooselyAttachedProperties((SimpleNode) attributes.get(0), attrProps);
                            attributes.set(0, cp);
                        }
                        if (!bdirProps.isEmpty()) {
                            if (bdirs.isEmpty()) {
                                // cannot assign properties
                                return null;
                            }
                            ComponentWithProperties cp = new ComponentWithLooselyAttachedProperties((SimpleNode) bdirs.get(0), bdirProps);
                            bdirs.set(0, cp);
                        }
                        if (!bindProps.isEmpty()) {
                            if (binds.isEmpty()) {
                                // cannot assign properties
                                return null;
                            }
                            ComponentWithProperties cp = new ComponentWithLooselyAttachedProperties((SimpleNode) binds.get(0), bindProps);
                            binds.set(0, cp);
                        }

                        //combinations
                        ComponentWithoutProperties aim = aims.size() > 1 ? new ComponentWithoutPropertiesCombination(LogicalOperator.AND, aims) : aims.get(0);
                        ComponentWithProperties attribute = attributes.size() > 1 ? new ComponentWithPropertiesCombination(LogicalOperator.AND, attributes) : attributes.get(0);
                        StatementOrComponentWithProperties bdir = bdirs.isEmpty() ? null : bdirs.size() > 1 ? new StatementOrComponentWithPropertiesCombination(LogicalOperator.AND, bdirs) : bdirs.get(0);
                        StatementOrComponentWithProperties bind = binds.isEmpty() ? null : binds.size() > 1 ? new StatementOrComponentWithPropertiesCombination(LogicalOperator.AND, binds) : binds.get(0);
                        StatementOrComponentWithoutProperties cac = cacs.isEmpty() ? null : cacs.size() > 1 ? new StatementOrComponentWithoutPropertiesCombination(LogicalOperator.AND, cacs) : cacs.get(0);
                        StatementOrComponentWithoutProperties cex = cexs.isEmpty() ? null : cexs.size() > 1 ? new StatementOrComponentWithoutPropertiesCombination(LogicalOperator.AND, cexs) : cexs.get(0);

                        RegulativeStatement r = new RegulativeStatement(attribute, aim, type, text, beg, end-beg);
                        if (deontic != null) {
                            r.setDeontic(deontic);
                        }
                        if (bdir != null) {
                            r.setDirectObject(bdir);
                        }
                        if (bind != null) {
                            r.setDirectObject(bind);
                        }
                        if (cac != null) {
                            r.setActivationCondition(cac);
                        }
                        if (cex != null) {
                            r.setExecutionConstraint(cex);
                        }
                        return r;
                    case "Constitutive Statement":
                        deontic = null;
                        cacs = new ArrayList<>();
                        cexs = new ArrayList<>();
                        List<ComponentWithProperties> entities = new ArrayList<>();
                        List<ComponentWithProperties> cProperties = new ArrayList<>();
                        List<ComponentWithoutProperties> functions= new ArrayList<>();
                        List<StatementOrComponentWithProperties> entityProps = new ArrayList<>();
                        List<StatementOrComponentWithProperties> propertyProps = new ArrayList<>();

                        for (Node c : children) {
                            Statement nested = null;
                            if (c.name.equals("Regulative Statement") || c.name.equals("Constitutive Statement") || c.name.equals("Fact/Observation")) {
                                if (c.children.size() != 1) {
                                    // nested statement has to have an annotation
                                    return null;
                                }
                                nested = c.children.get(0).createStatement(c.name);
                                if (nested == null) {
                                    // nested error
                                    return null;
                                }
                            }
                            Node n = nested == null ? c : c.children.get(0);

                            switch (n.name) {
                                case "(E) Constituted Entity":
                                    if (nested == null) {
                                        entities.add(n.simple());
                                    }
                                    else {
                                        //cannot be a statement
                                        return null;
                                    }
                                    break;
                                case "(F) Constitutive Function":
                                    if (nested == null) {
                                        functions.add(n.simple());
                                    }
                                    else {
                                        //cannot be a statement
                                        return null;
                                    }
                                    break;
                                case "(P) Constituting Property":
                                    if (nested == null) {
                                        cProperties.add(n.simple());
                                    }
                                    else {
                                        //cannot be a statement
                                        return null;
                                    }
                                    break;
                                case "(D) Deontic":
                                    if (deontic != null) {
                                        //there can only be one deontic
                                        return null;
                                    }
                                    if (nested != null) {
                                        //cannot be a statement
                                        return null;
                                    }
                                    if (isFact) {
                                        //cannot be in a sof
                                        return null;
                                    }
                                    deontic = n.simple();
                                    break;
                                case "(Cac) Activation Condition":
                                    cacs.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(Cex) Execution Constraint":
                                    cexs.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(E, prop) Constituted Entity Property":
                                    entityProps.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(P, prop) Constituting Property Property":
                                    propertyProps.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                default:
                                    //unexpected annotation type
                                    return null;
                            }
                        }

                        if (entities.isEmpty()) {
                            return null;
                        }
                        if (functions.isEmpty()) {
                            return null;
                        }

                        // add properties
                        if (!entityProps.isEmpty()) {
                            ComponentWithProperties cp = new ComponentWithLooselyAttachedProperties((SimpleNode) entities.get(0), entityProps);
                            entities.set(0, cp);
                        }
                        if (!propertyProps.isEmpty()) {
                            if (cProperties.isEmpty()) {
                                // cannot assign properties
                                return null;
                            }
                            ComponentWithProperties cp = new ComponentWithLooselyAttachedProperties((SimpleNode) cProperties.get(0), propertyProps);
                            cProperties.set(0, cp);
                        }

                        //combinations
                        cac = cacs.isEmpty() ? null : cacs.size() > 1 ? new StatementOrComponentWithoutPropertiesCombination(LogicalOperator.AND, cacs) : cacs.get(0);
                        cex = cexs.isEmpty() ? null : cexs.size() > 1 ? new StatementOrComponentWithoutPropertiesCombination(LogicalOperator.AND, cexs) : cexs.get(0);
                        ComponentWithProperties entity = entities.size() > 1 ? new ComponentWithPropertiesCombination(LogicalOperator.AND, entities) : entities.get(0);
                        ComponentWithoutProperties function = functions.size() > 1 ? new ComponentWithoutPropertiesCombination(LogicalOperator.AND, functions) : functions.get(0);
                        ComponentWithProperties cProperty = cProperties.isEmpty() ? null : cProperties.size() > 1 ? new ComponentWithPropertiesCombination(LogicalOperator.AND, cProperties) : cProperties.get(0);

                        ConstitutiveStatement c = new ConstitutiveStatement(entity, function, type, text, beg, end-beg);
                        if (cProperty != null) {
                            c.setConstitutingProperty(cProperty);
                        }
                        if (deontic != null) {
                            c.setDeontic(deontic);
                        }
                        if (cac != null) {
                            c.setActivationCondition(cac);
                        }
                        if (cex != null) {
                            c.setExecutionConstraint(cex);
                        }
                        return c;
                    default:
                        return null;
                }
            }
        }

        class Element implements Comparable<Element>
        {
            public int id;
            public String name;
            public double size;

            @Override
            public int compareTo(Element e) {
                return Double.compare(size, e.size);
            }
        }

        List<Node> roots = new ArrayList<>();
        Map<Integer, Node> nodes = new HashMap<>();
        Map<Integer, Double> idCounts = new HashMap<>();

        List<TsvColumn> headerColumns = doc.getSchema().getHeaderColumns(doc.getActiveColumns());

        // Get significant columns positions (we are also looking at column 2 to get the words and at column 1 to check for whitespaces)
        List<Integer> pos = new ArrayList<>();
        {
            int i = 0;
            for (TsvColumn c : headerColumns) {
                // "Component" gets 'webanno.custom.IGCoreConstitutiveSyntax-Component' and 'webanno.custom.IGCoreRegulativeSyntax-Component'
                // "Statementtype" gets 'webanno.custom.IGInstitutionalStatement-Statementtype'
                if (c.uimaFeature.getShortName().equals("Component") || c.uimaFeature.getShortName().equals("Statementtype")) {
                    pos.add(i + 3);
                }
                i++;
            }
        }

        for (TsvSentence sentence : doc.getSentences()) {
            // get column values for tokens
            List<TsvToken> tsvTokens = sentence.getTokens();
            List<String[]> tokens = new ArrayList<>();
            for (TsvToken t : tsvTokens) {
                String s = t.toString();
                tokens.add(s.split("\t"));
            }

            // get number of id occurrences in sentence to set correct order in tree paths (elements with lower counts are deeper in the tree)
            Pattern pattern = Pattern.compile("\\d+(?=])");
            for (String[] values : tokens) {
                for (int i : pos) {
                    Matcher matcher = pattern.matcher(values[i]);
                    while (matcher.find()) {
                        int match = Integer.parseInt(matcher.group());
                        double count = idCounts.containsKey(match) ? idCounts.get(match) : 0;
                        idCounts.put(match, count + 1 + i / 1e8); // if the number of occurrences is the same, the element from further layer is higher
                    }
                }
            }
        }
        for (TsvSentence sentence : doc.getSentences()) {
            // get column values for tokens
            List<TsvToken> tsvTokens = sentence.getTokens();
            List<String[]> tokens = new ArrayList<>();
            for (TsvToken t : tsvTokens) {
                String s = t.toString();
                tokens.add(s.split("\t"));
            }

            int prev = -1;
            for (String[] values : tokens) {
                // get elements with ids
                List<String> rawElements = new ArrayList<>();
                Pattern pattern = Pattern.compile("\\([^]]*]|Regulative Statement[^]]*]|Fact/Observation[^]]*]|Constitutive Statement[^]]*]");
                for (int i : pos) {
                    Matcher matcher = pattern.matcher(values[i]);
                    while (matcher.find()) {
                        rawElements.add(matcher.group());
                    }
                }
                List<Element> elements = new ArrayList<>();
                for (String re : rawElements) {
                    Element e = new Element();
                    e.id = Integer.parseInt(re.substring(re.indexOf('[') + 1, re.indexOf(']')));
                    e.name = re.substring(0, re.indexOf('['));
                    e.size = idCounts.get(e.id);
                    elements.add(e);
                }
                Collections.sort(elements);
                Collections.reverse(elements);

                int beg = Integer.parseInt(values[1].substring(0, values[1].indexOf('-')));
                int end = Integer.parseInt(values[1].substring(values[1].indexOf('-') + 1));

                // insert elements into the tree
                Node deepest = null;
                for (Element e : elements) {
                    if (nodes.containsKey(e.id)) {
                        if (beg != prev) //check for whitespace
                        {
                            nodes.get(e.id).text += " ";
                        }
                        nodes.get(e.id).text += values[2];
                    }
                    else {
                        nodes.put(e.id, new Node(e.id, e.name, values[2]));
                        nodes.get(e.id).beg = beg;
                        if (deepest == null) {
                            roots.add(nodes.get(e.id));
                        }
                        else {
                            deepest.children.add(nodes.get(e.id));
                        }
                    }
                    nodes.get(e.id).end = end;
                    deepest = nodes.get(e.id);
                }

                // add elements with no specified ids (if an element is one token long and has no relations?)
                // this kind of elements do not have consistent format and may be followed by various characters and that is why they are handled this way below
                rawElements = new ArrayList<>();
                // constitutive components
                pattern = Pattern.compile("(\\(D\\) Deontic|\\(Cac\\) Activation Condition|\\(Cex\\) Execution Constraint|\\(E\\) Constituted Entity(?! Property)|\\(F\\) Constitutive Function|\\(P\\) Constituting Property(?! Property)|\\(P, prop\\) Constituting Property Property|\\(E, prop\\) Constituted Entity Property)(?!\\[)");
                Matcher matcher = pattern.matcher(values[pos.get(0)]);
                while (matcher.find()) {
                    rawElements.add(matcher.group());
                }
                // regulative components
                pattern = Pattern.compile("(\\(D\\) Deontic|\\(I\\) Aim|\\(Cac\\) Activation Condition|\\(Cex\\) Execution Constraint|\\(A\\) Attribute(?!_)|\\(Bdir\\) Object_Direct(?!_)|\\(Bind\\) Object_Indirect(?!_)|\\(A, prop\\) Attribute_Property|\\(Bdir, prop\\) Object_Direct_Property|\\(Bind, prop\\) Object_Indirect_Property)(?!\\[)");
                matcher = pattern.matcher(values[pos.get(1)]);
                while (matcher.find()) {
                    rawElements.add(matcher.group());
                }
                for (String re : rawElements) {
                    Node n = new Node(-1, re, values[2]);
                    if (deepest != null) {
                        deepest.children.add(n);
                    }
                    else {
                        roots.add(n);
                    }
                    deepest = n;
                    n.beg = beg;
                    n.end = end;
                }

                // get last character position
                prev = end;
            }
        }

        List<Statement> statements = new ArrayList<>();
        for (Node r : roots) {
            AbstractStatement s = null;
            try {
                s = r.createStatement(r.name);
            } catch (InvalidIGDefinitionException e) {
                e.printStackTrace();
            }
            if(s != null) {
                statements.add(s);
            }
            else {
                //log errors?
            }
        }

        /*
        try (PrintWriter docOS = new PrintWriter(
                new OutputStreamWriter(buffer(getOutputStream(aJCas, filenameSuffix)), encoding))) {
            new Tsv3XSerializer().write(docOS, doc);
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }*/
    }
}
