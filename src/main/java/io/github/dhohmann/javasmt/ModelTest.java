package io.github.dhohmann.javasmt;

import org.sosy_lab.java_smt.SolverContextFactory;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.io.XmlExtendedFeatureModelFormat;
import org.spldev.formula.solver.SatSolver;
import org.spldev.formula.solver.javasmt.JavaSmtSolver;

public class ModelTest implements Runnable {

    private final XmlExtendedFeatureModelFormat modelFormat = new XmlExtendedFeatureModelFormat();
    private final String name;

    public ModelTest(String name) {
        this.name = name;
    }

    protected JavaSmtSolver createSolver(Formula formula) {
        return new JavaSmtSolver(formula, SolverContextFactory.Solvers.Z3);
    }

    @Override
    public void run() {

        //model.getFormula(); // Should fail
        System.out.println("=== " + name + " ===");
        System.out.println("Feature model:       " + Model.load(name, false, false).getStatistics());
        System.out.println(" - with attributes:  " +Model.load(name, true, false).getStatistics());
        System.out.println(" - with count:       " +Model.load(name, true, true).getStatistics());

        Model model = Model.load(name, true, true).appendConstraints();
        System.out.println(" - with constraints: " +Model.load(name, true, true).appendConstraints().getStatistics());
        System.out.println("\nTest Results: ");
        performTest(model.getFormula());
        System.out.println();

    }

    protected void performTest(Formula formula) {
        // Conversion from internal structure to JavaSMT structure
        long startConversion = System.currentTimeMillis();
        JavaSmtSolver solver = createSolver(formula);
        long endConversion = System.currentTimeMillis();
        System.out.println(" - Conversion to JavaSMT: " + (endConversion - startConversion) + " ms");

        // Analysis for solution
        long startHasSolution = System.currentTimeMillis();
        SatSolver.SatResult solution = solver.hasSolution();
        long endHasSolution = System.currentTimeMillis();
        System.out.println(" - Check if solvable :    " + (endHasSolution - startHasSolution) + " ms (Result: " + (solution)+")");

        // Analysis for number of solutions
        long startCountSolution = System.currentTimeMillis();
        long solutions = solver.countSolutions().longValue();
        long endCountSolution = System.currentTimeMillis();
        System.out.println(" - Counting solutions :   " + (endCountSolution - startCountSolution) + " ms (Result: "+solutions+")");
    }

}
