package io.github.dhohmann.javasmt;

import org.sosy_lab.java_smt.SolverContextFactory;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.atomic.Atomic;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.compound.And;
import org.spldev.formula.expression.io.ConfiguringConstraintsFormat;
import org.spldev.formula.expression.io.XmlExtendedFeatureModelFormat;
import org.spldev.formula.expression.term.Variable;
import org.spldev.formula.expression.term.bool.BoolVariable;
import org.spldev.formula.expression.term.integer.IntVariable;
import org.spldev.formula.expression.term.real.RealVariable;
import org.spldev.formula.solver.SatSolver;
import org.spldev.formula.solver.javasmt.JavaSmtSolver;
import org.spldev.util.data.Problem;
import org.spldev.util.data.Result;
import org.spldev.util.io.FileHandler;
import org.spldev.util.logging.Logger;
import org.spldev.util.tree.Trees;
import org.spldev.util.tree.structure.Tree;
import org.spldev.util.tree.visitor.TreeVisitor;

import java.io.InputStream;
import java.util.*;

public class ModelTest implements Runnable {

    private final XmlExtendedFeatureModelFormat modelFormat = new XmlExtendedFeatureModelFormat();

    private final Result<Formula> model;
    private final Result<Formula> constraints;
    private final String name;
    private final Formula formula;

    private Map<String, Object> statistics = new LinkedHashMap<>();


    public ModelTest(String name, boolean useConstraints) {
        this.name = name;

        model = FileHandler.load(getClass().getResourceAsStream("/" + name + "/model.xml"), modelFormat);

        if (useConstraints) {
            InputStream stream = getClass().getResourceAsStream("/" + name + "/constraints.xml");
            if (stream == null) {
                constraints = Result.empty(new Problem(new NullPointerException("No constraints present")));
            } else {
                ConfiguringConstraintsFormat constraintsFormat = new ConfiguringConstraintsFormat(model.orElse(Logger::logProblems).getVariableMap());
                constraints = FileHandler.load(stream, constraintsFormat);
            }
        } else {
            constraints = Result.empty();
        }

        // General Information
        formula = constraints.isPresent() ? new And(model.get(), constraints.get()) : model.get();
        VariableMap variableMap = VariableMap.fromExpression(formula);

        statistics.put("variables", variableMap.size());
        statistics.put("features", getFeatureCount(variableMap));
        statistics.put("literals", getLiterals(formula));
        statistics.put("attributes", getAttributes(variableMap));
        statistics.put("attributes.model", getModelAttributes(variableMap));
        statistics.put("variables.attributes", getAttributeVariableCount(variableMap));
    }

    public static class LiteralsCounter implements TreeVisitor<Integer, Tree<?>> {

        private int literals = 0;

        @Override
        public void reset() {
            literals = 0;
        }

        @Override
        public VisitorResult firstVisit(List<Tree<?>> path) {
            if (TreeVisitor.getCurrentNode(path) instanceof Atomic)
                literals++;
            return VisitorResult.Continue;
        }

        @Override
        public Integer getResult() {
            return literals;
        }
    }

    private int getLiterals(Formula formula) {
        return Trees.traverse(formula, new LiteralsCounter()).get();
    }

    public ModelTest(String name) {
        this(name, true);
    }

    protected JavaSmtSolver createSolver(Formula formula) {
        return new JavaSmtSolver(formula, SolverContextFactory.Solvers.Z3);
    }

    @Override
    public String toString() {
        return "ModelTest (" + name + "){" + '\n' +
                (constraints.isPresent() ? "\t(with constraints)" : "\t(no constraints)") + '\n' +
                "\tstatistics=" + statistics.toString() +
                '}';
    }

    @Override
    public void run() {
        // Conversion from internal structure to JavaSMT structure
        long startConversion = System.nanoTime();
        JavaSmtSolver solver = createSolver(formula);
        long endConversion = System.nanoTime();
        // TODO: May not be needed

        // Analysis for solution
        long startHasSolution = System.nanoTime();
        SatSolver.SatResult solution = solver.hasSolution();
        long endHasSolution = System.nanoTime();

        // Analysis for number of solutions
        long startCountSolution = System.nanoTime();
        long solutions = solver.countSolutions().longValue();
        long endCountSolution = System.nanoTime();
    }

    protected int getFeatureCount(VariableMap variableMap) {
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

    protected int getAttributeVariableCount(VariableMap variableMap) {
        int count = 0;
        for (String name : variableMap.getNames()) {
            Optional<Variable<?>> o = variableMap.getVariable(name);
            if (o.isPresent() && (o.get() instanceof IntVariable || o.get() instanceof RealVariable)) {
                count++;
            }
        }
        return count;
    }

    protected int getModelAttributes(VariableMap variableMap) {
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

}
