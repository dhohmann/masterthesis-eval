package io.github.dhohmann.javasmt;

import org.spldev.util.io.csv.CSVWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.*;
import java.util.logging.Formatter;
import java.util.regex.Pattern;

public class Prototype {

	static File FOLDER;
	static Logger LOGGER;

	static {
		FOLDER = new File(Prototype.class.getProtectionDomain().getCodeSource().getLocation().getFile());
		while (!FOLDER.isDirectory()) {
			FOLDER = FOLDER.getParentFile();
		}
		LOGGER = Logger.getLogger(Prototype.class.getName());
		try {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			File logFile = new File(FOLDER, "debug.log");
			FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath(), true);
			fileHandler.setFormatter(new Formatter() {
				@Override
				public String format(LogRecord record) {
					StringBuilder builder = new StringBuilder();

					builder.append(format.format(new Date(record.getMillis())));
					builder.append(" [").append(record.getLevel()).append("] ");
					builder.append(record.getMessage());

					Throwable t = record.getThrown();
					while (t != null) {
						builder.append("\nCaused by ");
						builder.append(t.getClass().getName()).append(":");
						builder.append(t.getMessage());
						t = t.getCause();
					}
					if (record.getThrown() != null) {
						StackTraceElement[] stack = record.getThrown().getStackTrace();
						for (StackTraceElement e : stack) {
							builder.append("\n\t at ").append(e.getFileName()).append(":").append(e.getLineNumber());
						}
					}
					builder.append("\n");
					return builder.toString();
				}
			});
			fileHandler.setEncoding(StandardCharsets.UTF_8.name());
			fileHandler.setLevel(Level.ALL);
			LOGGER.addHandler(fileHandler);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void copyModels(File folder) throws IOException {
		Collection<String> resources = ResourceList.getResources(Pattern.compile(".*model\\.xml"));
		for (String model : resources) {
			InputStream modelStream = Prototype.class.getResourceAsStream(model);
			String s = model.substring(model.indexOf("models/") + 8, model.indexOf("model.xml") - 1);
			FileOutputStream fileOutputStream = new FileOutputStream(new File(folder, s));
			byte[] buffer = new byte[256];
			int length = -1;
			while ((length = modelStream.read(buffer)) != -1) {
				fileOutputStream.write(buffer, 0, length);
			}
			modelStream.close();
			fileOutputStream.close();
		}
	}

	public static List<File> getModels() {
		List<File> models = new ArrayList<>();
		File modelFolder = new File(FOLDER, "models");
		if (!modelFolder.exists()) {
			modelFolder.mkdir();
			try {
				LOGGER.info("Copying models from jar");
				copyModels(modelFolder);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		File[] files = modelFolder.listFiles((f) -> f.isDirectory());
		if (files.length > 0) {
			Arrays.stream(files).forEach(f -> models.add(f));
		}
		return models;
	}

	public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

		CommandLineParser parser = new CommandLineParser(args);

		boolean stats = parser.getFlag("stats");
		String[] iArgs = parser.getArgumentValue("i");
		if (iArgs != null && iArgs.length > 0) {
			int iterations = Integer.parseInt(iArgs[0]);
			if (iterations <= 0) {
				System.out.println("Iterations cannot be 0 or negative");
				System.exit(4);
			} else {
				ModelTest.ITERATIONS = iterations;
			}
		}

		if (args.length > 0) {
			if ("stats".equals(args[0])) {
				stats = true;
			}
		}

		CSVWriter csv = new CSVWriter();
		csv.setAppend(true);
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

		csv.setFileName(new File(FOLDER, "model" + dateFormat.format(date) + ".csv").getAbsolutePath());
		csv.setSeparator(";");
		csv.setHeader(new ArrayList<>());
		csv.addHeaderValue("model");
		csv.addHeaderValue("features");
		csv.addHeaderValue("literals");
		csv.addHeaderValue("attributes");
		csv.addHeaderValue("features with attributes");
		csv.addHeaderValue("variables");
		csv.addHeaderValue("variables for aggregations");
		csv.addHeaderValue("configuring constraints");
		csv.addHeaderValue("creation");
		csv.addHeaderValue("hasSolution");
		csv.addHeaderValue("countSolutions");

		List<File> tests = getModels();
		if (tests.isEmpty()) {
			System.exit(4);
		}
		for (File name : tests) {
			final ModelTest test = new ModelTest(name, csv);
			if (stats) {
				test.printStatistics();
			} else {
				CompletableFuture f = test.execute();
				f.thenRun(() -> {
					LOGGER.info("Finished " + test);
				});
				f.get();
			}
		}

//        ModelTest pc_config = new ModelTest("pc_config");
//        pc_config.printStatistics();
//
//        ModelTest busy_box = new ModelTest("busy_box");
//        busy_box.printStatistics();

//        System.out.println(new ModelTest("sandwich"));
//        System.out.println(new ModelTest("webserver"));
//        System.out.println(new ModelTest("pc_config"));
//        System.out.println(new ModelTest("busy_box"));
//        System.out.println(new ModelTest("linux"));
	}

}
