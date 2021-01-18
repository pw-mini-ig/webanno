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
    @ConfigurationParameter(name = PARAM_FILENAME_EXTENSION, mandatory = true, defaultValue = ".yaml")
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

            public AbstractStatement createStatement(String statementName, List<ExportLog> logs) throws InvalidIGDefinitionException {
                boolean isFact = statementName.equals("Fact/Observation");
                AtomicStatementType type = isFact ? AtomicStatementType.statementOfFact : AtomicStatementType.institutionalStatement;
                if (isFact) {
                    for (Node c : children) {
                        if (c.name.equals("(A) Attribute")) {
                            statementName = "Regulative Statement";
                            break;
                        }
                        if (c.name.equals("(E) Constituted Entity")) {
                            statementName = "Constitutive Statement";
                            break;
                        }
                    }
                    if(statementName.equals("Fact/Observation")) {
                        logs.add(new ExportLog(this, true, "missing attribute or constituted entity"));
                        return null;
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
                                    logs.add(new ExportLog(c, true, "nested statement has to have another annotation"));
                                    return null;
                                }
                                nested = c.children.get(0).createStatement(c.name, logs);
                                if (nested == null) {
                                    return null;
                                }
                            }
                            else if (c.children.size() > 0) {
                                logs.add(new ExportLog(c, false, "should be a nested statement?"));
                            }
                            Node n = nested == null ? c : c.children.get(0);

                            switch (n.name) {
                                case "(A) Attribute":
                                    if (nested == null) {
                                        attributes.add(n.simple());
                                    }
                                    else {
                                        logs.add(new ExportLog(n, true, "cannot be a nested statement"));
                                        return null;
                                    }
                                    break;
                                case "(I) Aim":
                                    if (nested == null) {
                                        aims.add(n.simple());
                                    }
                                    else {
                                        logs.add(new ExportLog(n, true, "cannot be a nested statement"));
                                        return null;
                                    }
                                    break;
                                case "(Bdir) Object\\_Direct":
                                    bdirs.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(Bind) Object\\_Indirect":
                                    binds.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(D) Deontic":
                                    if (deontic != null) {
                                        logs.add(new ExportLog(n, true, "there can only be one deontic"));
                                        return null;
                                    }
                                    if (nested != null) {
                                        logs.add(new ExportLog(n, true, "cannot be a nested statement"));
                                        return null;
                                    }
                                    if (isFact) {
                                        logs.add(new ExportLog(n, true, "cannot be in a statement of fact"));
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
                                case "(A, prop) Attribute\\_Property":
                                    attrProps.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(Bdir, prop) Object\\_Direct\\_Property":
                                    bdirProps.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                case "(Bind, prop) Object\\_Indirect\\_Property":
                                    bindProps.add(Objects.requireNonNullElseGet(nested, n::simple));
                                    break;
                                default:
                                    logs.add(new ExportLog(n, true, "unexpected annotation type"));
                                    return null;
                            }
                        }
                        if (attributes.isEmpty()) {
                            logs.add(new ExportLog(this, true, "missing attribute"));
                            return null;
                        }
                        if (aims.isEmpty()) {
                            logs.add(new ExportLog(this, true, "missing aim"));
                            return null;
                        }

                        // add properties
                        if (!attrProps.isEmpty()) {
                            ComponentWithProperties cp = new ComponentWithLooselyAttachedProperties((SimpleNode) attributes.get(0), attrProps);
                            attributes.set(0, cp);
                        }
                        if (!bdirProps.isEmpty()) {
                            if (bdirs.isEmpty()) {
                                logs.add(new ExportLog(this, true, "missing direct object"));
                                return null;
                            }
                            ComponentWithProperties cp = new ComponentWithLooselyAttachedProperties((SimpleNode) bdirs.get(0), bdirProps);
                            bdirs.set(0, cp);
                        }
                        if (!bindProps.isEmpty()) {
                            if (binds.isEmpty()) {
                                logs.add(new ExportLog(this, true, "missing indirect object"));
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
                                    logs.add(new ExportLog(c, true, "nested statement has to have another annotation"));
                                    return null;
                                }
                                nested = c.children.get(0).createStatement(c.name, logs);
                                if (nested == null) {
                                    return null;
                                }
                            }
                            else if (c.children.size() > 0) {
                                logs.add(new ExportLog(c, false, "should be a nested statement?"));
                            }
                            Node n = nested == null ? c : c.children.get(0);

                            switch (n.name) {
                                case "(E) Constituted Entity":
                                    if (nested == null) {
                                        entities.add(n.simple());
                                    }
                                    else {
                                        logs.add(new ExportLog(n, true, "cannot be a nested statement"));
                                        return null;
                                    }
                                    break;
                                case "(F) Constitutive Function":
                                    if (nested == null) {
                                        functions.add(n.simple());
                                    }
                                    else {
                                        logs.add(new ExportLog(n, true, "cannot be a nested statement"));
                                        return null;
                                    }
                                    break;
                                case "(P) Constituting Property":
                                    if (nested == null) {
                                        cProperties.add(n.simple());
                                    }
                                    else {
                                        logs.add(new ExportLog(n, true, "cannot be a nested statement"));
                                        return null;
                                    }
                                    break;
                                case "(D) Deontic":
                                    if (deontic != null) {
                                        logs.add(new ExportLog(n, true, "there can only be one deontic"));
                                        return null;
                                    }
                                    if (nested != null) {
                                        logs.add(new ExportLog(n, true, "cannot be a nested statement"));
                                        return null;
                                    }
                                    if (isFact) {
                                        logs.add(new ExportLog(n, true, "cannot be in a statement of fact"));
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
                                    logs.add(new ExportLog(n, true, "unexpected annotation type"));
                                    return null;
                            }
                        }

                        if (entities.isEmpty()) {
                            logs.add(new ExportLog(this, true, "missing constituted entity"));
                            return null;
                        }
                        if (functions.isEmpty()) {
                            logs.add(new ExportLog(this, true, "missing constitutive function"));
                            return null;
                        }

                        // add properties
                        if (!entityProps.isEmpty()) {
                            ComponentWithProperties cp = new ComponentWithLooselyAttachedProperties((SimpleNode) entities.get(0), entityProps);
                            entities.set(0, cp);
                        }
                        if (!propertyProps.isEmpty()) {
                            if (cProperties.isEmpty()) {
                                logs.add(new ExportLog(this, true, "missing constituting property"));
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
                        logs.add(new ExportLog(this, true, "has to be inside a statement"));
                        return null;
                }
            }

            class ExportLog
            {
                Node n;
                boolean error;
                String message;

                private String getType() {
                    return error ? "Error: \"" : "Warning: \"";
                }

                private String getNodeInfo() {
                    return "\" at: " + n.name + " \"" + n.text + "\" " + n.beg + "-" + n.end;
                }

                @Override
                public String toString() {
                    return getType() + message + getNodeInfo();
                }

                public ExportLog(Node n, boolean error, String message) {
                    this.n = n;
                    this.error = error;
                    this.message = message;
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

        List<String> whole = new ArrayList<>();
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

        int prev = 0;
        for (TsvSentence sentence : doc.getSentences()) {
            // get column values for tokens
            List<TsvToken> tsvTokens = sentence.getTokens();
            List<String[]> tokens = new ArrayList<>();
            for (TsvToken t : tsvTokens) {
                String s = t.toString();
                tokens.add(s.split("\t"));
            }

            Pattern pattern = Pattern.compile("\\d+(?=])");
            for (String[] values : tokens) {
                //get whole text
                switch (Integer.parseInt(values[1].substring(0, values[1].indexOf('-'))) - prev) {
                    case 0:
                        break;
                    default:
                        whole.add(" ");
                    /* the more sophisticated variant, trying to replicate the original formatting
                    case 1:
                        whole.add(" ");
                        break;
                    case 2:
                        whole.add("\n");
                        break;
                    case 3:
                        whole.add(" \n");
                        break;
                    default:
                        whole.add("\n\n");
                        break;
                     */
                }
                whole.add(values[2]);
                prev = Integer.parseInt(values[1].substring(values[1].indexOf('-') + 1));

                // get number of id occurrences in sentence to set correct order in tree paths (elements with lower counts are deeper in the tree)
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

            prev = -1;
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
                pattern = Pattern.compile("(\\(D\\) Deontic|\\(I\\) Aim|\\(Cac\\) Activation Condition|\\(Cex\\) Execution Constraint|\\(A\\) Attribute(?!\\\\)|\\(Bdir\\) Object\\\\_Direct(?!\\\\)|\\(Bind\\) Object\\\\_Indirect(?!\\\\)|\\(A, prop\\) Attribute\\\\_Property|\\(Bdir, prop\\) Object\\\\_Direct\\\\_Property|\\(Bind, prop\\) Object\\\\_Indirect\\\\_Property)(?!\\[)");
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
        List<Node.ExportLog> logs = new ArrayList<>();
        for (Node r : roots) {
            AbstractStatement s = null;
            try {
                s = r.createStatement(r.name, logs);
            } catch (InvalidIGDefinitionException e) {
                e.printStackTrace();
            }
            if (s != null) {
                statements.add(s);
            }
        }

        RootElement root = new RootElement(String.join("", whole), statements);
        String yaml = IgSchemaUtilities.generateYaml(root);

        try (PrintWriter docOS = new PrintWriter(
                new OutputStreamWriter(buffer(getOutputStream(aJCas, filenameSuffix)), encoding))) {
            docOS.printf("%s", yaml);

            if (!logs.isEmpty()) {
                docOS.printf("\n\n# Logs from export:\n");
                for (Node.ExportLog l : logs) {
                    docOS.printf("# %s\n", l.toString());
                }
            }
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }
}
