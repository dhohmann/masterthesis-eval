package io.github.dhohmann.javasmt;

import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.compound.And;
import org.spldev.formula.expression.io.ConfiguringConstraintsFormat;
import org.spldev.formula.expression.io.XmlExtendedFeatureModelFormat;
import org.spldev.formula.expression.term.Variable;
import org.spldev.formula.expression.term.bool.BoolVariable;
import org.spldev.formula.expression.term.integer.IntVariable;
import org.spldev.formula.expression.term.real.RealVariable;
import org.spldev.util.data.Problem;
import org.spldev.util.data.Result;
import org.spldev.util.io.FileHandler;
import org.spldev.util.logging.Logger;
import org.spldev.util.tree.Trees;

import java.io.InputStream;
import java.util.*;

public class Model {

    private final String name;
    private Result<Formula> model, constraints;

    private boolean containsCustomAttributes = false;
    private boolean containsConstraints = false;
    private boolean containsCount = false;
    private boolean containsAttributes = false;

    private Model(String name) {
        this.name = name;
    }

    public static Model load(String name) {
        return Model.load(name, true, true);
    }

    public static Model load(String name, boolean useAttributes, boolean generateCount) {
        return new Model(name).load(new HashMap<>(), useAttributes, generateCount);
    }

    public Model load(HashMap<String, Object> attributes, boolean useAttributes, boolean generateCount) {
        XmlExtendedFeatureModelFormat modelFormat = new XmlExtendedFeatureModelFormat(attributes);
        modelFormat.setUseAttributes(useAttributes);
        modelFormat.setGenerateCount(generateCount);

        if (!attributes.isEmpty()) {
            containsCustomAttributes = true;
        }
        if (useAttributes) {
            containsAttributes = true;
        }

        if (generateCount) {
            containsCount = true;
        }

        model = FileHandler.load(getClass().getResourceAsStream("/" + name + "/model.xml"), modelFormat);
        return this;
    }

    public Model appendConstraints() {
        if (model != null) {
            InputStream stream = getClass().getResourceAsStream("/" + name + "/constraints.xml");
            if (stream == null) {
                constraints = Result.empty(new Problem(new NullPointerException("No constraints present")));
            } else {
                ConfiguringConstraintsFormat constraintsFormat = new ConfiguringConstraintsFormat(model.orElse(Logger::logProblems).getVariableMap());
                constraints = FileHandler.load(stream, constraintsFormat);
            }
            containsConstraints = true;
        } else {
            System.out.println("Load model first");
        }
        return this;
    }


    public Statistics getStatistics() {
        Statistics statistics = new Statistics(containsCustomAttributes, containsConstraints, containsCount, containsAttributes);
        if (model == null) {
            return statistics;
        }

        if (!model.isPresent()) {
            throw new RuntimeException("Model " + name + " was not found");
        }


        Formula formula = getFormula();

        VariableMap variableMap = VariableMap.fromExpression(formula);
        statistics.setFeatureCount(getFeatureCount(variableMap));
        statistics.setLiteralCount(getLiterals(formula));
        statistics.setAttributeCount(getAttributes(variableMap));
        statistics.setAttributeValueCount(getModelAttributes(variableMap));
        statistics.setVariableCount(getVariableCount(variableMap));
        statistics.setAttributeVariableCount(getAttributeVariableCount(variableMap));
        return statistics;
    }

    public Formula getFormula() {
        Formula formula = model.get();
        if (constraints != null && constraints.isPresent()) {
            formula = new And(formula, constraints.get());
        }
        return formula;
    }

    private int getVariableCount(VariableMap variableMap) {
        return variableMap.getNames().size();
    }

    private int getLiterals(Formula formula) {
        return Trees.traverse(formula, new LiteralsCounter()).get();
    }

    private int getFeatureCount(VariableMap variableMap) {
        int count = 0;
        for (String name : variableMap.getNames()) {
            Optional<Variable<?>> o = variableMap.getVariable(name);
            if (o.isPresent() && o.get() instanceof BoolVariable) {
                count++;
            }
        }
        return count;
    }

    private int getAttributes(VariableMap variableMap) {
        List<String> featureAttributes = new ArrayList<>();
        for (String name : variableMap.getNames()) {
            if (name.matches("(.*)\\(.*\\)")) {
                String attribute = name.substring(0, name.indexOf("("));
                if (!featureAttributes.contains(attribute)) {
                    featureAttributes.add(attribute);
                }
            }
        }
        return featureAttributes.size();
    }

    private int getAttributeVariableCount(VariableMap variableMap) {
        int count = 0;
        for (String name : variableMap.getNames()) {
            Optional<Variable<?>> o = variableMap.getVariable(name);
            if (o.isPresent() && (o.get() instanceof IntVariable || o.get() instanceof RealVariable)) {
                count++;
            }
        }
        return count;
    }

    private int getModelAttributes(VariableMap variableMap) {
        int count = 0;
        for (String name : variableMap.getNames()) {
            if (name.matches("(.*)\\(.*\\)")) {
                Optional<Variable<?>> o = variableMap.getVariable(name);
                if (o.isPresent() && (o.get() instanceof IntVariable || o.get() instanceof RealVariable)) {
                    count++;
                }
            }
        }
        return count;
    }

    public final class Statistics {

        private int features = -1;
        private int literals = -1;
        private int attributes = -1;
        private int variables = -1;
        private int attributeValues = -1;
        private int attributeVariables;

        private boolean containsCustomAttributes = false;
        private boolean containsConstraints = false;
        private boolean containsCount = false;
        private boolean containsAttributes = false;

        public Statistics(boolean customAttributes, boolean constraints, boolean count, boolean attributes) {
            containsCustomAttributes = customAttributes;
            containsAttributes = attributes;
            containsConstraints = constraints;
            containsCount = count;
        }

        void setFeatureCount(int count) {
            this.features = count;
        }

        void setLiteralCount(int count) {
            this.literals = count;
        }

        void setAttributeCount(int count) {
            this.attributes = count;
        }

        void setVariableCount(int count) {
            this.variables = count;
        }

        void setAttributeValueCount(int modelAttributes) {
            this.attributeValues = modelAttributes;
        }

        void setAttributeVariableCount(int attributeVariableCount) {
            this.attributeVariables = attributeVariableCount;
        }

        @Override
        public String toString() {
            return "Statistics (" + name + "){" +
                    "features=" + features +
                    ", literals=" + literals +
                    ", attributes=" + attributes +
                    ", features with attributes=" + attributeValues +
                    ", variables=" + variables +
                    ", variables for count=" + (variables - attributeValues - features) +
                    ", variables for aggr=" + attributeVariables +
                    ", customAttributes=" + containsCustomAttributes +
                    ", constraints included=" + containsConstraints +
                    ", containsCount=" + containsCount +
                    ", containsAttributes=" + containsAttributes +
                    '}';
        }

        public int getAttributes() {
            return attributes;
        }

        public int getAttributeValues() {
            return attributeValues;
        }

        public int getAttributeVariables() {
            return attributeVariables;
        }

        public int getFeatures() {
            return features;
        }

        public int getLiterals() {
            return literals;
        }

        public int getVariables() {
            return variables;
        }
    }

}
