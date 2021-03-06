package jadx.api;

import jadx.core.Jadx;
import jadx.core.ProcessClass;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.visitors.IDexTreeVisitor;
import jadx.core.dex.visitors.SaveCode;
import jadx.core.utils.ErrorsCounter;
import jadx.core.utils.exceptions.CodegenException;
import jadx.core.utils.exceptions.DecodeException;
import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.core.utils.files.InputFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jadx API usage example:
 * <pre><code>
 *  Decompiler jadx = new Decompiler();
 *  jadx.loadFile(new File("classes.dex"));
 *  jadx.setOutputDir(new File("out"));
 *  jadx.save();
 * </code></pre>
 * <p/>
 * Instead of 'save()' you can get list of decompiled classes:
 * <pre><code>
 *  for(JavaClass cls : jadx.getClasses()) {
 *      System.out.println(cls.getCode());
 *  }
 * </code></pre>
 */
public final class Decompiler {
	private static final Logger LOG = LoggerFactory.getLogger(Decompiler.class);

	private final IJadxArgs args;
	private final List<InputFile> inputFiles = new ArrayList<InputFile>();

	private File outDir;

	private RootNode root;
	private List<IDexTreeVisitor> passes;

	public Decompiler() {
		this.args = new DefaultJadxArgs();
		init();
	}

	public Decompiler(IJadxArgs jadxArgs) {
		this.args = jadxArgs;
		init();
	}

	public void setOutputDir(File outDir) {
		this.outDir = outDir;
		init();
	}

	void init() {
		if (outDir == null) {
			outDir = new File("jadx-output");
		}
		this.passes = Jadx.getPassesList(args, outDir);
	}

	public void loadFile(File file) throws IOException, DecodeException {
		loadFiles(Collections.singletonList(file));
	}

	public void loadFiles(List<File> files) throws IOException, DecodeException {
		if (files.isEmpty()) {
			throw new JadxRuntimeException("Empty file list");
		}
		inputFiles.clear();
		for (File file : files) {
			inputFiles.add(new InputFile(file));
		}
		parse();
	}

	public void save() {
		try {
			ExecutorService ex = getSaveExecutor();
			ex.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			LOG.error("Save interrupted", e);
		}
	}

	public ThreadPoolExecutor getSaveExecutor() {
		if (root == null) {
			throw new JadxRuntimeException("No loaded files");
		}
		int threadsCount = args.getThreadsCount();
		LOG.debug("processing threads count: {}", threadsCount);

		final List<IDexTreeVisitor> passList = new ArrayList<IDexTreeVisitor>(passes);
		SaveCode savePass = new SaveCode(outDir, args);
		passList.add(savePass);

		LOG.info("processing ...");
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
		for (final ClassNode cls : root.getClasses(false)) {
			if (cls.getCode() == null) {
				Runnable job = new Runnable() {
					@Override
					public void run() {
						ProcessClass.process(cls, passList);
					}
				};
				executor.execute(job);
			} else {
				try {
					savePass.visit(cls);
				} catch (CodegenException e) {
					LOG.error("Can't save class {}", cls, e);
				}
			}
		}
		executor.shutdown();
		return executor;
	}

	public List<JavaClass> getClasses() {
		List<ClassNode> classNodeList = root.getClasses(false);
		List<JavaClass> classes = new ArrayList<JavaClass>(classNodeList.size());
		for (ClassNode classNode : classNodeList) {
			classes.add(new JavaClass(this, classNode));
		}
		return Collections.unmodifiableList(classes);
	}

	public List<JavaPackage> getPackages() {
		List<JavaClass> classes = getClasses();
		Map<String, List<JavaClass>> map = new HashMap<String, List<JavaClass>>();
		for (JavaClass javaClass : classes) {
			String pkg = javaClass.getPackage();
			List<JavaClass> clsList = map.get(pkg);
			if (clsList == null) {
				clsList = new ArrayList<JavaClass>();
				map.put(pkg, clsList);
			}
			clsList.add(javaClass);
		}
		List<JavaPackage> packages = new ArrayList<JavaPackage>(map.size());
		for (Map.Entry<String, List<JavaClass>> entry : map.entrySet()) {
			packages.add(new JavaPackage(entry.getKey(), entry.getValue()));
		}
		Collections.sort(packages);
		for (JavaPackage pkg : packages) {
			Collections.sort(pkg.getClasses(), new Comparator<JavaClass>() {
				@Override
				public int compare(JavaClass o1, JavaClass o2) {
					return o1.getShortName().compareTo(o2.getShortName());
				}
			});
		}
		return Collections.unmodifiableList(packages);
	}

	public int getErrorsCount() {
		return ErrorsCounter.getErrorCount();
	}

	void parse() throws DecodeException {
		ClassInfo.clearCache();
		ErrorsCounter.reset();

		root = new RootNode();
		LOG.info("loading ...");
		root.load(inputFiles);
	}

	void processClass(ClassNode cls) {
		LOG.info("processing class {} ...", cls);
		ProcessClass.process(cls, passes);
	}

	RootNode getRoot() {
		return root;
	}
}
