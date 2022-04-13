package io.github.dhohmann.javasmt;

import org.sosy_lab.java_smt.SolverContextFactory;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.io.XmlExtendedFeatureModelFormat;
import org.spldev.formula.solver.SatSolver;
import org.spldev.formula.solver.javasmt.JavaSmtSolver;
import org.spldev.util.io.csv.CSVWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static io.github.dhohmann.javasmt.Prototype.LOGGER;

public class ModelTest implements Runnable {

	public static int ITERATIONS = 5;
	public static final long TIMEOUT = 30;
	public static final TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

	private final XmlExtendedFeatureModelFormat modelFormat = new XmlExtendedFeatureModelFormat();
	private final String name;
	private final CSVWriter csv;
	private final File folder;

	public ModelTest(File modelFolder, CSVWriter csv) throws IOException {
		this.folder = modelFolder;
		this.name = folder.getName();
		this.csv = csv;
	}

	protected CompletableFuture<JavaSmtSolver> createSolver(Formula formula) {
		CompletableFuture<JavaSmtSolver> creation = new CompletableFuture<>();
		creation.completeAsync(() -> new JavaSmtSolver(formula, SolverContextFactory.Solvers.Z3));
		return creation;
	}

	public CompletableFuture execute() {
		return CompletableFuture.runAsync(this);
	}

	@Override
	public void run() {
		performTest(Model.load(folder, false, false), true); // DRY-RUN

		Model notExtended = Model.load(folder, false, false);
		for (int i = 0; i < ITERATIONS; i++) {
			performTest(notExtended);
		}

		Model extendedAttributesNoCount = Model.load(folder, true, false);
		for (int i = 0; i < ITERATIONS; i++) {
			performTest(extendedAttributesNoCount);
		}
		Model extendedAttributesCount = Model.load(folder, true, true);
		for (int i = 0; i < ITERATIONS; i++) {
			performTest(extendedAttributesCount);
		}

		Model extendedAttributesCountConstraints = Model.load(folder, true, true).appendConstraints();
		if (extendedAttributesCountConstraints.getConstraints() > 0) {
			for (int i = 0; i < ITERATIONS; i++) {
				performTest(extendedAttributesCountConstraints);
			}
		} else {
			LOGGER.info("[" + name + "] No constraints present");
		}

	}

	public void printStatistics() {
		LOGGER.info("=== " + name + " ===");
		LOGGER.info("Feature model:       " + Model.load(folder, false, false).getStatistics());
		LOGGER.info(" - with attributes:  " + Model.load(folder, true, false).getStatistics());
		LOGGER.info(" - with count:       " + Model.load(folder, true, true).getStatistics());
		LOGGER.info(" - with constraints: " + Model.load(folder, true, true).appendConstraints()
			.getStatistics());
	}

	protected void performTest(Model model) {
		System.gc();
		performTest(model, false);
	}

	protected void performTest(Model model, boolean dryRun) {
		Model.Statistics stats = model.getStatistics();
		LOGGER.info("Running " + stats);

		List<String> line = new ArrayList<>();

		if (!dryRun) {
			line.add(name + stats.getNameSuffix());
			line.add(Integer.toString(stats.getFeatures()));
			line.add(Integer.toString(stats.getLiterals()));
			line.add(Integer.toString(stats.getAttributes()));
			line.add(Integer.toString(stats.getAttributeValues()));
			line.add(Integer.toString(stats.getVariables()));
			line.add(Integer.toString(stats.getAttributeVariables()));
			line.add(Integer.toString(stats.getConfiguringConstraints()));
		}
		Formula formula = model.getFormula();
		// Conversion from internal structure to JavaSMT structure
		if (!dryRun) {
			LOGGER.info("[" + name + "] Conversion starting");
		}
		JavaSmtSolver solver = null;
		try {
			long startConversion = System.currentTimeMillis();
			solver = createSolver(formula).get(TIMEOUT, TIMEOUT_UNIT);
			long endConversion = System.currentTimeMillis();
			CompletableFuture<Long> creation = CompletableFuture.completedFuture(endConversion - startConversion);
			if (!dryRun) {
				LOGGER.info("[" + name + "] Conversion finished");
				line.add(Long.toString(creation.get()));
			}
		} catch (Exception e) {
			if (!dryRun) {
				LOGGER.log(Level.INFO, "[" + name + "] Conversion timeout ", e);

				line.add(Long.toString(-1L));
			}
		}
		try {

			// Analysis for solution
			if (!dryRun) {
				LOGGER.info("[" + name + "] Solution Check starting");
			}
			CompletableFuture<Long> hasSolution = hasSolution(solver);
			if (!dryRun) {
				LOGGER.info("[" + name + "] Solution Check finished");
				line.add(Long.toString(hasSolution.get(TIMEOUT, TIMEOUT_UNIT)));
			}
		} catch (Exception e) {
			if (!dryRun) {
				LOGGER.log(Level.INFO, "[" + name + "] Solution Check timeout ", e);

				line.add(Long.toString(-1L));
			}
		}
		/*
		 * try { // Analysis for number of solutions if (!dryRun) { LOGGER.info("[" +
		 * name + "] Solution Count starting"); } CompletableFuture<Long> countSolutions
		 * = countSolutions(solver); if (!dryRun) {
		 * line.add(Long.toString(countSolutions.get(TIMEOUT, TIMEOUT_UNIT)));
		 * LOGGER.info("[" + name + "] Solution Count finished"); } } catch (Exception
		 * e) { if (!dryRun) { LOGGER.log(Level.INFO, "[" + name +
		 * "] Solution Count timeout", e);
		 * 
		 * line.add(Long.toString(-1L)); } }
		 */

		line.add(Long.toString(-1L));
		if (!dryRun) {
			appendTestResult(line);
		}
		solver.shutdownManager.requestShutdown("[" + name + "] Finished execution");
	}

	private synchronized void appendTestResult(List<String> line) {
		csv.addLine(line);
		csv.flush();
	}

	public CompletableFuture<Long> hasSolution(JavaSmtSolver solver) {
		CompletableFuture<Long> future = new CompletableFuture<>();

		CompletableFuture<Long> shutdown = new CompletableFuture<>();
		long millisToSleep = TIMEOUT_UNIT.toMillis(TIMEOUT) > 100 ? 100 : TIMEOUT_UNIT.toMillis(TIMEOUT);
		long shouldBeFinishedByNow = System.currentTimeMillis() + TIMEOUT_UNIT.toMillis(TIMEOUT);

		shutdown.completeAsync(() -> {
			while (!future.isDone() && System.currentTimeMillis() < shouldBeFinishedByNow) {
				try {
					Thread.sleep(millisToSleep);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (future.isDone()) {
				try {
					return future.get();
				} catch (Exception e) {
					return -1L;
				}
			} else {
				LOGGER.info("Requesting shutdown");
				solver.shutdownManager.requestShutdown("Timeout");
				shutdown.completeExceptionally(new Exception("Timeout"));
			}
			return -1L;
		});

		future.completeAsync(() -> {
			long startHasSolution = System.currentTimeMillis();
			SatSolver.SatResult solution = solver.hasSolution();
			long endHasSolution = System.currentTimeMillis();
			return endHasSolution - startHasSolution;
		}).completeOnTimeout(-1L, 1, TimeUnit.SECONDS);
		return shutdown;
	}

	public CompletableFuture<Long> countSolutions(JavaSmtSolver solver) {
		CompletableFuture<Long> future = new CompletableFuture<>();

		long shouldBeFinishedByNow = System.currentTimeMillis() + TIMEOUT_UNIT.toMillis(TIMEOUT);
		long millisToSleep = TIMEOUT_UNIT.toMillis(TIMEOUT) > 100 ? 100 : TIMEOUT_UNIT.toMillis(TIMEOUT);
		CompletableFuture<Long> shutdown = new CompletableFuture<>();
		shutdown.supplyAsync(() -> {
			while (!future.isDone() && System.currentTimeMillis() < shouldBeFinishedByNow) {
				try {
					Thread.sleep(millisToSleep);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (future.isDone()) {
				try {
					return future.get();
				} catch (Exception e) {
					shutdown.completeExceptionally(e);
				}
			} else {
				LOGGER.info("Requesting shutdown");
				solver.shutdownManager.requestShutdown("Timeout");
			}
			return -1L;
		}).exceptionally(t -> {
			LOGGER.info("Requesting shutdown");
			solver.shutdownManager.requestShutdown("Timeout");
			return -1L;
		});

		future.completeAsync(() -> {
			long startCountSolution = System.currentTimeMillis();
			solver.countSolutions();
			long endCountSolution = System.currentTimeMillis();
			return endCountSolution - startCountSolution;
		}).completeOnTimeout(-1L, 1, TimeUnit.SECONDS);
		return shutdown;
	}

	@Override
	public String toString() {
		return "ModelTest{" +
			"name='" + name + "'}";
	}
}
