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

import static org.apache.commons.io.IOUtils.buffer;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tudarmstadt.ukp.clarin.webanno.tsv.internal.tsv3x.model.*;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.dkpro.core.api.io.JCasFileWriter_ImplBase;
import org.dkpro.core.api.parameter.ComponentParameters;

import de.tudarmstadt.ukp.clarin.webanno.tsv.internal.tsv3x.Tsv3XCasDocumentBuilder;
import de.tudarmstadt.ukp.clarin.webanno.tsv.internal.tsv3x.Tsv3XCasSchemaAnalyzer;
import de.tudarmstadt.ukp.clarin.webanno.tsv.internal.tsv3x.Tsv3XSerializer;

/**
 * Writes the WebAnno TSV v3.x format.
 */
public class WebannoTsv3XWriter
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
            public List<Node> children = new ArrayList<>();

            public Node(int id, String name, String text) {
                this.id = id;
                this.name = name;
                this.text = text;
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
            Pattern pattern = Pattern.compile("\\d+(?=\\])");
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

            String prev = null;
            for (String[] values : tokens) {
                // get elements with ids
                List<String> rawElements = new ArrayList<>();
                pattern = Pattern.compile("\\([^\\]]*\\]|Regulative Statement[^\\]]*\\]|Fact\\/Observation[^\\]]*\\]|Constitutive Statement[^\\]]*\\]");
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

                // insert elements into the tree
                Node deepest = null;
                for (Element e : elements) {
                    if (nodes.containsKey(e.id)) {
                        if (!values[1].substring(0, values[1].indexOf('-')).equals(prev)) //check for whitespace
                        {
                            nodes.get(e.id).text += " ";
                        }
                        nodes.get(e.id).text += values[2];
                        deepest = nodes.get(e.id);
                    }
                    else {
                        nodes.put(e.id, new Node(e.id, e.name, values[2]));
                        if (deepest == null) {
                            roots.add(nodes.get(e.id));
                        }
                        else {
                            deepest.children.add(nodes.get(e.id));
                        }
                        deepest = nodes.get(e.id);
                    }
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
                }

                // get last character position
                prev = values[1].substring(values[1].indexOf('-') + 1);
            }
        }

        for (Node r : roots) {
            // create yaml classes from trees
        }









        try (PrintWriter docOS = new PrintWriter(
                new OutputStreamWriter(buffer(getOutputStream(aJCas, filenameSuffix)), encoding))) {
            new Tsv3XSerializer().write(docOS, doc);
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }


}
