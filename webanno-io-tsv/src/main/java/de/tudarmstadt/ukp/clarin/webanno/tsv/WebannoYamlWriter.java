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
            public int id; // -1 if not specified
            public String name;
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

            public AbstractStatement createStatement(String type) throws InvalidIGDefinitionException {
                switch(type) {
                    case "Regulative Statement":
                        SimpleNode deontic = null;
                        List<StatementOrComponentWithProperties> bdirProps = new ArrayList<>();
                        List<StatementOrComponentWithProperties> bindProps = new ArrayList<>();
                        List<StatementOrComponentWithProperties> attrProps = new ArrayList<>();
                        List<ComponentWithoutProperties> aims = new ArrayList<>();
                        List<ComponentWithoutProperties> cacs = new ArrayList<>();
                        List<ComponentWithoutProperties> cexs = new ArrayList<>();
                        List<ComponentWithProperties> bdirs = new ArrayList<>();
                        List<ComponentWithProperties> binds = new ArrayList<>();
                        List<Statement> bdirs_s = new ArrayList<>();
                        List<Statement> binds_s = new ArrayList<>();
                        List<Statement> cacs_s = new ArrayList<>();
                        List<Statement> cexs_s = new ArrayList<>();
                        List<ComponentWithProperties> attributes = new ArrayList<>();

                        for (Node c : children) {
                            Statement nested = null;
                            if (c.name.equals("Regulative Statement") || c.name.equals("Constitutive Statement") || c.name.equals("Fact/Observation")) {
                                if (c.children.size() != 1) {
                                    // nested statement has to have an annotation
                                    return null;
                                }
                                nested = c.children.get(0).createStatement(c.name);
                                if (nested == null) {
                                    return null;
                                }
                            }
                            Node n;
                            if (nested != null) {
                                n = c.children.get(0);
                            }
                            else {
                                n = c;
                            }

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
                                case "(Bdir) Object\\_Direct":
                                    if (nested == null) {
                                        bdirs.add(n.simple());
                                    }
                                    else {
                                        bdirs_s.add(nested);
                                    }
                                    break;
                                case "(Bind) Object\\_Indirect":
                                    if (nested == null) {
                                        binds.add(n.simple());
                                    }
                                    else {
                                        binds_s.add(nested);
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
                                    deontic = n.simple();
                                    break;
                                case "(Cac) Activation Condition":
                                    if (nested == null) {
                                        cacs.add(n.simple());
                                    }
                                    else {
                                        cacs_s.add(nested);
                                    }
                                    break;
                                case "(Cex) Execution Constraint":
                                    if (nested == null) {
                                        cexs.add(n.simple());
                                    }
                                    else {
                                        cexs_s.add(nested);
                                    }
                                    break;
                                case "(A, prop) Attribute\\_Property":
                                    if (nested == null) {
                                        attrProps.add(n.simple());
                                    }
                                    else {
                                        attrProps.add(nested);
                                    }
                                    break;
                                case "(Bdir, prop) Object\\_Direct\\_Property":
                                    if (nested == null) {
                                        bdirProps.add(n.simple());
                                    }
                                    else {
                                        bdirProps.add(nested);
                                    }
                                    break;
                                case "(Bind, prop) Object\\_Indirect\\_Property":
                                    if (nested == null) {
                                        bindProps.add(n.simple());
                                    }
                                    else {
                                        bindProps.add(nested);
                                    }
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
                        if (!bdirs.isEmpty() && !bdirs_s.isEmpty()) {
                            return null;
                        }
                        if (!binds.isEmpty() && !binds_s.isEmpty()) {
                            return null;
                        }
                        if (!cacs.isEmpty() && !cacs_s.isEmpty()) {
                            return null;
                        }
                        if (!cexs.isEmpty() && !cexs_s.isEmpty()) {
                            return null;
                        }

                        // add properties
                        if (!attrProps.isEmpty()) {
                            if (attributes.isEmpty()) {
                                // cannot assign properties
                                return null;
                            }
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
                        ComponentWithProperties attribute;
                        ComponentWithoutProperties aim;
                        StatementOrComponentWithProperties bdir = null;
                        StatementOrComponentWithProperties bind = null;
                        StatementOrComponentWithoutProperties cac = null;
                        StatementOrComponentWithoutProperties cex = null;

                        if (attributes.size() > 1) {
                            attribute = new ComponentWithPropertiesCombination(LogicalOperator.AND, attributes);
                        }
                        else {
                            attribute = attributes.get(0);
                        }
                        if (aims.size() > 1) {
                            aim = new ComponentWithoutPropertiesCombination(LogicalOperator.AND, aims);
                        }
                        else {
                            aim = aims.get(0);
                        }
                        if (!bdirs.isEmpty()) {
                            if (bdirs.size() > 1) {
                                bdir = new ComponentWithPropertiesCombination(LogicalOperator.AND, bdirs);
                            }
                            else {
                                bdir = bdirs.get(0);
                            }
                        }
                        if (!bdirs_s.isEmpty()) {
                            if (bdirs_s.size() > 1) {
                                bdir = new StatementCombination(LogicalOperator.AND, bdirs_s, "");
                            }
                            else {
                                bdir = bdirs_s.get(0);
                            }
                        }
                        if (!binds.isEmpty()) {
                            if (binds.size() > 1) {
                                bind = new ComponentWithPropertiesCombination(LogicalOperator.AND, binds);
                            }
                            else {
                                bind = binds.get(0);
                            }
                        }
                        if (!binds_s.isEmpty()) {
                            if (binds_s.size() > 1) {
                                bind = new StatementCombination(LogicalOperator.AND, binds_s, "");
                            }
                            else {
                                bind = bdirs_s.get(0);
                            }
                        }
                        if (!cacs.isEmpty()) {
                            if (cacs.size() > 1) {
                                cac = new ComponentWithoutPropertiesCombination(LogicalOperator.AND, cacs);
                            }
                            else {
                                cac = cacs.get(0);
                            }
                        }
                        if (!cacs_s.isEmpty()) {
                            if (cacs_s.size() > 1) {
                                cac = new StatementCombination(LogicalOperator.AND, cacs_s, "");
                            }
                            else {
                                cac = cacs_s.get(0);
                            }
                        }
                        if (!cexs.isEmpty()) {
                            if (cexs.size() > 1) {
                                cex = new ComponentWithoutPropertiesCombination(LogicalOperator.AND, cexs);
                            }
                            else {
                                cex = cexs.get(0);
                            }
                        }
                        if (!cexs_s.isEmpty()) {
                            if (cexs_s.size() > 1) {
                                cex = new StatementCombination(LogicalOperator.AND, cexs_s, "");
                            }
                            else {
                                cex = cexs_s.get(0);
                            }
                        }

                        RegulativeStatement r = new RegulativeStatement(attribute, aim, AtomicStatementType.institutionalStatement, text, beg, end-beg);
                        if (bdir != null) {
                            r.setDirectObject(bdir);
                        }
                        if (bind != null) {
                            r.setDirectObject(bind);
                        }
                        if (deontic != null) {
                            r.setDeontic(deontic);
                        }
                        if (cac != null) {
                            r.setActivationCondition(cac);
                        }
                        if (cex != null) {
                            r.setExecutionConstraint(cex);
                        }
                        return r;
                    case "Constitutive Statement":
                        return null;
                    case "Fact/Observation":
                        return null;
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
            Map<Integer, Double> idCounts = new HashMap<Integer, Double>();
            Pattern pattern = Pattern.compile("\\d+(?=])");
            for (String[] values : tokens) {
                for (int i : pos) {
                    Matcher matcher = pattern.matcher(values[i]);
                    while (matcher.find()) {
                        int match = Integer.parseInt(matcher.group());
                        double count = idCounts.containsKey(match) ? idCounts.get(match) : 0;
                        idCounts.put(match, count + 1 + i/1e8); // if the number of occurrences is the same, the element from further layer is higher
                    }
                }
            }

            int prev = -1;
            for (String[] values : tokens) {
                // get elements with ids
                List<String> rawElements = new ArrayList<>();
                pattern = Pattern.compile("\\([^]]*]|Regulative Statement[^]]*]|Fact/Observation[^]]*]|Constitutive Statement[^]]*]");
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

                // add elements with no specified ids (if an element is one token long and has no relations, I think it only happens in the constitutive components column)
                rawElements = new ArrayList<>();
                pattern = Pattern.compile("(\\(D\\)\\sDeontic|\\(E\\)\\sConstituted\\sEntity|\\(F\\)\\sConstitutive\\sFunction|\\(P\\)\\sConstituting\\sProperty(?!\\sProperty)|\\(P,\\sprop\\)\\sConstituting\\sProperty\\sProperty)(?!\\[)");
                Matcher matcher = pattern.matcher(values[pos.get(0)]);
                while (matcher.find()) {
                    rawElements.add(matcher.group());
                }
                for (String re : rawElements) {
                    Node n = new Node(-1, re, values[2]);
                    if (deepest != null) { // it shouldn't be null, because everything should be in a statement and they have ids (but safer to check)
                        deepest.children.add(n);
                        deepest = n;
                    }
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
