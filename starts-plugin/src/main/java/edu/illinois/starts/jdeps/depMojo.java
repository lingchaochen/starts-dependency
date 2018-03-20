package edu.illinois.starts.jdeps;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.surefire.booter.Classpath;

import edu.illinois.starts.constants.StartsConstants;
import edu.illinois.starts.enums.DependencyFormat;
import edu.illinois.starts.helpers.EkstaziHelper;
import edu.illinois.starts.helpers.Writer;
import edu.illinois.starts.helpers.ZLCHelper;
import edu.illinois.starts.jdeps.BaseMojo.Result;
import edu.illinois.starts.util.Logger;
import edu.illinois.starts.util.Pair;

@Mojo(name = "dep", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST_COMPILE)
public class depMojo extends BaseMojo implements StartsConstants {
	static String baseDIR = System.getProperty("user.dir");
    private static final String TARGET = "target";
    /**
     * Set this to "false" to prevent checksums from being persisted to disk. This
     * is useful for "dry runs" where one may want to see the non-affected tests that
     * STARTS writes to the Surefire excludesFile, without updating test dependencies.
     */
    @Parameter(property = "updateRunChecksums", defaultValue = TRUE)
    protected boolean updateRunChecksums;
    /**
     * Set this to "false" to disable smart hashing, i.e., to *not* strip
     * Bytecode files of debug info prior to computing checksums. See the "Smart
     * Checksums" Sections in the Ekstazi paper:
     * http://dl.acm.org/citation.cfm?id=2771784
     */
    @Parameter(property = "cleanBytes", defaultValue = TRUE)
    protected boolean cleanBytes;

    /**
     * Set this to "true" to update test dependencies on disk. The default value of "false"
     * is useful for "dry runs" where one may want to see the diff without updating
     * the test dependencies.
     */
    @Parameter(property = "updateDiffChecksums", defaultValue = FALSE)
    private boolean updateDiffChecksums;
    public void execute() throws MojoExecutionException {
    	Logger.getGlobal().setLoggingLevel(Level.parse(loggingLevel));

        Set<String> changed = new HashSet<>();
        Set<String> nonAffected = new HashSet<>();
        Pair<Set<String>, Set<String>> data = computeChangeData(false);
        String extraText = EMPTY;
        if (data != null) {
            nonAffected = data.getKey();
            changed = data.getValue();
        } else {
            extraText = " (no RTS artifacts; likely the first run)";
        }
        printResult(changed, "ChangedClasses" + extraText);
        File fp = new File(baseDIR+"/Tests_dep");
        if (!fp.exists()) {  
            fp.mkdir();// 目录不存在的情况下，会抛出异常  
        }
        
        try {
			checkdep(nonAffected);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    }
    protected Pair<Set<String>, Set<String>> computeChangeData(boolean writeChanged) throws MojoExecutionException {
        long start = System.currentTimeMillis();
        Pair<Set<String>, Set<String>> data = null;
        if (depFormat == DependencyFormat.ZLC) {
            ZLCHelper zlcHelper = new ZLCHelper();
            data = zlcHelper.getChangedData(getArtifactsDir(), cleanBytes);
        } else if (depFormat == DependencyFormat.CLZ) {
            data = EkstaziHelper.getNonAffectedTests(getArtifactsDir());
        }
        Set<String> changed = data == null ? new HashSet<String>() : data.getValue();
        if (writeChanged || Logger.getGlobal().getLoggingLevel().intValue() <= Level.FINEST.intValue()) {
            Writer.writeToFile(changed, CHANGED_CLASSES, getArtifactsDir());
        }
        long end = System.currentTimeMillis();
        Logger.getGlobal().log(Level.FINE, "[PROFILE] COMPUTING CHANGES: " + Writer.millsToSeconds(end - start));
        return data;
    }


  public void checkdep(Set<String> nonAffected) throws Exception{
	Classpath sfClassPath = getSureFireClassPath();
    String sfPathString = Writer.pathToString(sfClassPath.getClassPath());
    List<String> allTests = getTestClasses("updateForNextRun");
    boolean computeUnreached = true;
    Set<String> affectedTests = new HashSet<>(allTests);
    ClassLoader loader = createClassLoader(sfClassPath);
    Result result = prepareForNextRun(sfPathString, sfClassPath, allTests, nonAffected, computeUnreached);
    Map<String, Set<String>> Testsdep = result.getTestDeps(); 
    writeresults(Testsdep);
}
  
  public void writeresults(Map m1) throws Exception {

		FileWriter fstream = new FileWriter(baseDIR+"/Tests_dep/Tests_dep.txt");
		BufferedWriter out = new BufferedWriter(fstream);
		Iterator<Entry<String, HashSet>> it = m1.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, HashSet> pairs = it.next();
			out.write(pairs.getKey()+":"+pairs.getValue() + "\n");
		}
		out.close();
}
  
}