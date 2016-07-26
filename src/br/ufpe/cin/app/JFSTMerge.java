package br.ufpe.cin.app;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import br.ufpe.cin.exceptions.PrintException;
import br.ufpe.cin.exceptions.SemistructuredMergeException;
import br.ufpe.cin.exceptions.TextualMergeException;
import br.ufpe.cin.files.FilesManager;
import br.ufpe.cin.files.FilesTuple;
import br.ufpe.cin.logging.LoggerFactory;
import br.ufpe.cin.mergers.SemistructuredMerge;
import br.ufpe.cin.mergers.TextualMerge;
import br.ufpe.cin.mergers.util.MergeContext;
import br.ufpe.cin.mergers.util.MergeScenario;
import br.ufpe.cin.printers.Prettyprinter;
import br.ufpe.cin.statistics.Statistics;

/**
 * Main class, responsible for performing <i>semistructured</i> merge in java files.
 * It also merges non java files, however, in these cases, traditional linebased
 * (unstructured) merge is invoked.
 * @author Guilherme
 */
public class JFSTMerge {

	//log of activities
	private static final Logger LOGGER = LoggerFactory.make(false);

	/**
	 * Merges merge scenarios, indicated by .revisions files. 
	 * This is mainly used for evaluation purposes.
	 * A .revisions file contains the directories of the revisions to merge in top-down order: 
	 * first revision, base revision, second revision (three-way merge).
	 * @param revisionsPath file path
	 */
	public MergeScenario mergeRevisions(String revisionsPath){
		MergeScenario scenario = null;
		try{
			//reading the .revisions file line by line to get revisions directories
			List<String> listRevisions = new ArrayList<>();
			BufferedReader reader = Files.newBufferedReader(Paths.get(revisionsPath));
			listRevisions = reader.lines().collect(Collectors.toList());
			if(listRevisions.size()!=3) throw new Exception("Invalid .revisions file!");

			//merging the identified directories
			if(!listRevisions.isEmpty()){
				LOGGER.log(Level.INFO,"MERGING SCENARIO: " + revisionsPath);

				System.out.println("MERGING REVISIONS: \n" 
						+ listRevisions.get(0) + "\n"
						+ listRevisions.get(1) + "\n"
						+ listRevisions.get(2)
						);

				String revisionFileFolder = (new File(revisionsPath)).getParent();
				String leftDir  = revisionFileFolder+ File.separator+ listRevisions.get(0);
				String baseDir  = revisionFileFolder+ File.separator+ listRevisions.get(1);
				String rightDir = revisionFileFolder+ File.separator+ listRevisions.get(2);

				List<FilesTuple> mergedTuples = mergeDirectories(leftDir, baseDir, rightDir, null);

				//using the name of the revisions directories as revisions identifiers
				scenario = new MergeScenario(revisionsPath, listRevisions.get(0), listRevisions.get(1), listRevisions.get(2), mergedTuples);

				//printing the resulting merged codes
				Prettyprinter.generateMergedScenario(scenario);
			}
		} catch(Exception e){
			System.err.println("An error occurred. See the jfstmerge.log file for more details.\n Send the log to gjcc@cin.ufpe.br for analysis if preferable.");
			LOGGER.log(Level.SEVERE,"",e);
			System.exit(-1);
		}
		return scenario;
	}

	/**
	 * Merges directories.
	 * @param leftDirPath (mine)
	 * @param baseDirPath (older)
	 * @param rightDirPath (yours)
	 * @param outputDirPath can be null, in this case, the output will only be printed in the console.
	 * @return merged files tuples
	 */
	public List<FilesTuple> mergeDirectories(String leftDirPath, String baseDirPath, String rightDirPath, String outputDirPath){
		List<FilesTuple> filesTuple = FilesManager.fillFilesTuples(leftDirPath, baseDirPath, rightDirPath);
		for(FilesTuple tuple : filesTuple){
			File left = tuple.getLeftFile();
			File base = tuple.getBaseFile();
			File right= tuple.getRightFile();

			//merging the file tuple
			MergeContext context = mergeFiles(left, base, right, null);
			tuple.setContext(context);

			//printing the resulting merged code
			try{
				Prettyprinter.generateMergedTuple(outputDirPath, tuple);
			} catch (PrintException pe) {
				System.err.println("An error occurred. See the jfstmerge.log file for more details.\n Send the log to gjcc@cin.ufpe.br for analysis if preferable.");
				LOGGER.log(Level.SEVERE,"",pe);
				System.exit(-1);
			}
		}
		return filesTuple;
	}

	/**
	 * Three-way semistructured merge of the given .java files.
	 * @param left (mine) version of the file, or <b>null</b> in case of intentional empty file. 
	 * @param base (older) version of the file, or <b>null</b> in case of intentional empty file. 
	 * @param right (yours) version of the file, or <b>null</b> in case of intentional empty file. 
	 * @param outputFilePath of the merged file. Can be <b>null</b>, in this case, the output will only be printed in the console.
	 * @return context with relevant information gathered during the merging process.
	 */
	public MergeContext mergeFiles(File left, File base, File right, String outputFilePath){
		FilesManager.validateFiles(left, base, right);		
		System.out.println("MERGING FILES: \n" 
				+ ((left != null)?left.getAbsolutePath() :"<empty left>") + "\n"
				+ ((base != null)?base.getAbsolutePath() :"<empty base>") + "\n"
				+ ((right!= null)?right.getAbsolutePath():"<empty right>")
				);

		MergeContext context = new MergeContext(left,base,right,outputFilePath);

		//there is no need to call specific merge algorithms in equal or consistenly changes files
		if(FilesManager.areFilesDifferent(left,base,right,outputFilePath,context)){
			try{
				//run unstructured merge first is necessary due to future steps.
				context.unstructuredOutput 	= TextualMerge.merge(left, base, right, false);		
				context.semistructuredOutput= SemistructuredMerge.merge(left, base, right,context);

			} catch(TextualMergeException tme){ //textual merge must work even when semistructured not, so this exception precedes others
				System.err.println("An error occurred. See the jfstmerge.log file for more details.\n Send the log to gjcc@cin.ufpe.br for analysis if preferable.");
				LOGGER.log(Level.SEVERE,"",tme);
				System.exit(-1);

			} catch(SemistructuredMergeException sme){
				//in case of any error during the merging process, merge with unstructured merge //log it
				LOGGER.log(Level.WARNING,"",sme);
				context.semistructuredOutput=context.unstructuredOutput;
			}
		}

		//printing the resulting merged code
		try {
			Prettyprinter.printOnScreenMergedCode(context);
			Prettyprinter.generateMergedFile(context, outputFilePath);
		} catch (PrintException pe) {
			System.err.println("An error occurred. See the jfstmerge.log file for more details.\n Send the log to gjcc@cin.ufpe.br for analysis if preferable.");
			LOGGER.log(Level.SEVERE,"",pe);
			System.exit(-1);
		}

		//computing statistics
		Statistics.compute(context);

		System.out.println("Merge files finished.");

		return context;
	}

	public static void main(String[] args) {
		/*		try {
			PrintStream pp = new PrintStream(new File("output-file.txt"));
			System.setOut(pp);
			System.setErr(pp);
		} catch (Exception e) {
			e.printStackTrace();
		}*/

		/*				new JFSTMerge().mergeFiles(
						new File("C:\\Users\\Guilherme\\Desktop\\test\\left\\Teste.java"), 
						new File("C:\\Users\\Guilherme\\Desktop\\test\\base\\Teste.java"), 
						null,  
						"C:\\Users\\Guilherme\\Desktop\\test\\Test.java");*/

		/*				new JFSTMerge().mergeFiles(
						new File("C:\\Users\\Guilherme\\Google Drive\\P�s-Gradua��o\\Pesquisa\\Outros\\running_examples\\exemplos diff3\\voldemort\\left\\Repartitioner.java"), 
						new File("C:\\Users\\Guilherme\\Google Drive\\P�s-Gradua��o\\Pesquisa\\Outros\\running_examples\\exemplos diff3\\voldemort\\base\\Repartitioner.java"), 
						new File("C:\\Users\\Guilherme\\Google Drive\\P�s-Gradua��o\\Pesquisa\\Outros\\running_examples\\exemplos diff3\\voldemort\\right\\Repartitioner.java"), 
						"C:\\Users\\Guilherme\\Desktop\\test\\Test.java");*/


		//new JFSTMerge().mergeRevisions("C:\\tstfstmerge\\java_lucenesolr\\rev_dc62b_aff97\\rev_dc62b-aff97.revisions");

		//TODO
		//C:\\tstfstmerge\\java_retrofit\\rev_941ae_2ef7c\\rev_left_941ae\\retrofit\\src\\main\\java\\retrofit\\http\\Header.java
		//C:\\tstfstmerge\\java_retrofit\\rev_941ae_2ef7c\\rev_941ae-2ef7c.revisions

		/*		try {
			List<String> listRevisions = new ArrayList<>();
			BufferedReader reader;
			reader = Files.newBufferedReader(Paths.get("C:\\tstfstmerge\\all.revisions"));
			listRevisions = reader.lines().collect(Collectors.toList());
			long t0 = System.currentTimeMillis();
			for(String r : listRevisions){
				new JFSTMerge().mergeRevisions(r);
			}
			long tf = System.currentTimeMillis();
			System.out.println((tf - t0)/1000);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/

		/*		new JFSTMerge().mergeFiles(
				new File("C:\\Users\\Guilherme\\Desktop\\test\\left\\Test.java"), 
				new File("C:\\Users\\Guilherme\\Desktop\\test\\base\\Test.java"), 
				new File("C:\\Users\\Guilherme\\Desktop\\test\\right\\Test.java"),  
				null);*/

		/*		new JFSTMerge().mergeFiles(
				new File("C:\\Users\\Guilherme\\Desktop\\testequals\\left\\Test.java"), 
				new File("C:\\Users\\Guilherme\\Desktop\\testequals\\base\\Test.java"), 
				new File("C:\\Users\\Guilherme\\Desktop\\testequals\\right\\Test.java"),  
				null);*/

		/*				new JFSTMerge().mergeFiles(
				new File("C:\\Users\\Guilherme\\Desktop\\testequals\\left\\Test.java"), 
				null, 
				new File("C:\\Users\\Guilherme\\Desktop\\testequals\\right\\Test.java"),  
				null);*/

		/*		new JFSTMerge().mergeDirectories(
				"C:\\Users\\Guilherme\\Desktop\\testimage\\left", 
				"C:\\Users\\Guilherme\\Desktop\\testimage\\base", 
				"C:\\Users\\Guilherme\\Desktop\\testimage\\right", 
				null);*/
		
		new JFSTMerge().mergeRevisions("C:\\Users\\Guilherme\\Desktop\\test\\rev.revisions");
	}
}