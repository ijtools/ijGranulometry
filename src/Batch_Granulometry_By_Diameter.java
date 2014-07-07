import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.ContrastEnhancer;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ijt.analysis.granulometry.GrayscaleGranulometry;
import ijt.analysis.granulometry.GrayscaleGranulometry.Operation;
import ijt.filter.morphology.Strel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Locale;


/**
 * 
 */

/**
 * @author David Legland
 *
 */
public class Batch_Granulometry_By_Diameter implements PlugIn {

	public enum Enhancement {
		NONE("None"),
		NORMALIZE("Normalize"),
		EQUALIZE("Equalize");
		
		private String label;
		
		private Enhancement(String label) {
			this.label = label;
		}
		
		public String toString() {
			return this.label;
		}
		
		public static String[] getAllLabels(){
			int n = Enhancement.values().length;
			String[] result = new String[n];
			
			int i = 0;
			for (Enhancement v : Enhancement.values())
				result[i++] = v.toString();
			
			return result;
		}
		
		/**
		 * Determines the operation type from its label.
		 * @throws IllegalArgumentException if label is not recognized.
		 */
		public static Enhancement fromLabel(String label) {
			if (label != null)
				label = label.toLowerCase();
			for (Enhancement val : Enhancement.values()) {
				String cmp = val.toString().toLowerCase();
				if (cmp.equals(label))
					return val;
			}
			throw new IllegalArgumentException("Unable to parse Enhancement with label: " + label);
		}
	};

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {

		// (1) Choose an image into the input directory
		ImagePlus image0 = IJ.openImage();
		if (image0 == null) 
			return;
		
		// Compute the list of images in input directory
		FileInfo infos = image0.getOriginalFileInfo();
		File baseDir = new File(infos.directory);
		File[] fileList = baseDir.listFiles();
		
		// (2) Open a dialog to choose analysis parameters
		GenericDialog gd = new GenericDialog("Batch Grayscale Granulometry");
		
		gd.addChoice("Operation", Operation.getAllLabels(), 
				Operation.CLOSING.toString());
		gd.addChoice("Element", Strel.Shape.getAllLabels(), 
				Strel.Shape.SQUARE.toString());
		gd.addNumericField("Diameter Max. (in pixels)", 51, 0);
		gd.addNumericField("Step (in pixels)", 1, 0);
		gd.addChoice("Contrast_Adjustment", Enhancement.getAllLabels(), 
				Enhancement.NONE.toString());
		gd.addNumericField("Spatial_Calibration", 1, 2);
		gd.addStringField("Calibration_Unit", "pixel");
		// Could also add an option for the type of operation
		
		// Wait for user response
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		
		// extract chosen parameters
		Operation op = Operation.fromLabel(gd.getNextChoice());
		Strel.Shape shape = Strel.Shape.fromLabel(gd.getNextChoice());
		int diamMax = (int) gd.getNextNumber();		
		int step 	= (int) gd.getNextNumber();
		Enhancement enhancement = Enhancement.fromLabel(gd.getNextChoice());
		double resol = gd.getNextNumber();
		if (Double.isNaN(resol)) {
			IJ.error("Parsing Error", "Could not interpret the resolution input");
			return;
		}
		String unitName = gd.getNextString();
		
		// (3) Open a dialog to choose the result file name
		String defaultName = createDefaultFileName(baseDir.getPath(), op,
				shape, diamMax, step, enhancement);
		OpenDialog dlg = new OpenDialog("Save Result file", baseDir.getParent(), defaultName);
		String outputDirName = dlg.getDirectory();
		String fileName = dlg.getFileName();
		if (fileName == null) {
			return;
		}
		
		// (4) Compute the granulometric curves
		Object[] results = exec(fileList, op, shape, diamMax, step, enhancement, resol, unitName);
		
		
		// (5) process results
		ResultsTable volumeTable = (ResultsTable) results[1];
		volumeTable.show("Image Volumes");
		
		ResultsTable granuloTable = (ResultsTable) results[3];
		granuloTable.show("Granulometries");
		
		ResultsTable statsTable = (ResultsTable) results[5];
		statsTable.show("Granulometry Stats");

		
		plotVolumetryCurves(volumeTable, "Volume variations", unitName);
		plotGranulometryCurves(granuloTable, "Granulometry", unitName);
		
		String outputFilePath = new File(outputDirName, fileName).getAbsolutePath();
		saveSummaryFile(outputFilePath, fileList, op, shape, diamMax, step, enhancement, resol, unitName);

		String basePath = outputFilePath;
		if (basePath.endsWith(".txt"))
			basePath = basePath.substring(0, basePath.length() - 4);
		fileName = basePath.concat(".vols.txt");
		saveResultsTable(fileName, volumeTable);
		
		fileName = basePath.concat(".gr.txt");
		saveResultsTable(fileName, granuloTable);
		
		fileName = basePath.concat(".stats.txt");
		saveResultsTable(fileName, statsTable);
	}

	private final static String createDefaultFileName(String baseDir, Operation op, 
			Strel.Shape shape, int diamMax, int step, Enhancement enhanceType) {
		String baseName = new File(baseDir).getName() + "_";
		
		switch (enhanceType) {
		case NORMALIZE:
			baseName = baseName + "norm_";
			break;
		case EQUALIZE:
			baseName = baseName + "eq_";
			break;
		case NONE:
			// nothing to do
			break;
		}
		
		String opName = op.toString().substring(0, 2);

		String shapeName = "Unk";
		switch(shape) {
		case DISK: 			shapeName = "Dsk"; break;
		case SQUARE: 		shapeName = "Sq"; break;
		case OCTAGON: 		shapeName = "Oct"; break;
		case DIAMOND: 		shapeName = "Dmd"; break;
		case LINE_HORIZ: 	shapeName = "LinH"; break;
		case LINE_VERT: 	shapeName = "LinV"; break;
		case LINE_DIAG_UP: 	shapeName = "LinU"; break;
		case LINE_DIAG_DOWN: shapeName = "LinD"; break;
		}
		
		String diamString = Integer.toString(diamMax);
		String stepString = step == 1 ? "" : "s" + Integer.toString(step);
		
		return baseName + opName + shapeName + diamString + stepString + ".txt";
	}
	
	public Object[] exec(File[] fileList, Operation op, Strel.Shape shape,
			int diamMax, int step, Enhancement enhanceType, double resol,
			String unitName) {
		
		// Number of times the morphological operation should be applied
		// Diameter = 1 corresponds to original image
		int nSteps = (diamMax - 1) / step;
		
		// Initialize the array of column names
		String[] varNames = new String[nSteps+1];
		if (resol != 1 || unitName.compareTo("pixel") != 0) {
			for (int i = 0; i < nSteps + 1; i++) {
				double diam = (i * step + 1) * resol;
				varNames[i] = String.format(Locale.US, "%5.2f", diam);
			}
		} else {
			for (int i = 0; i < nSteps + 1; i++) {
				varNames[i] = Integer.toString(i * step + 1);
			}
		}
		
		// Initialize array of image volumes
		double[] volumes = new double[nSteps + 1];
		
		ImagePlus demoImage = IJ.openImage(fileList[0].getAbsolutePath());
		demoImage.show();
		
		// Initialize two tables: one for volumes, one for granulos
		// Each table has image names as labels, and strel sizes as columns names.
		// granulo table has one column less than volume table
		ResultsTable volumeTable = new ResultsTable();
		ResultsTable granuloTable = new ResultsTable();
		

		ContrastEnhancer enhancer = new ContrastEnhancer();
		
		// Iterate on image list
		for (int iImg = 0; iImg < fileList.length; iImg++) {

			// Read and extract current image processor 
			ImagePlus imp = IJ.openImage(fileList[iImg].getAbsolutePath());
			if (imp == null)
				break;

			ImageProcessor image = imp.getProcessor();
			
			// Ensure input image is Gray 8
			if (image instanceof ShortProcessor) {
				image = image.convertToByte(true);
			}
			
			// Eventually add normalisation process
			switch (enhanceType) {
			case NORMALIZE:
				enhancer.stretchHistogram(image, .05);
				break;
			case EQUALIZE:
				enhancer.equalize(image);
				break;
			case NONE:
				// nothing to do
				break;
			}
			
			// Update the display figure
			demoImage.setImage(imp);
			demoImage.setTitle(imp.getTitle());
			demoImage.setProcessor(image);
			demoImage.repaintWindow();
			
			// Compute initial volume of image
			double vol = GrayscaleGranulometry.imageVolume(image);
			volumes[0] = vol;

			// Store initial volume
			volumeTable.incrementCounter();
			volumeTable.addLabel(fileList[iImg].getName());
			volumeTable.addValue(varNames[0], vol);

			// Iterate on the different strel diameters
			int diam = 1;
			for (int i = 0; i < nSteps; i++) {
				// Compute and display current size 
				diam += step;
				IJ.showStatus("Diameter " + diam + "(" + (i+1) + "/" + nSteps + ")");

				// create structuring element for current size
				Strel strel = shape.fromDiameter(diam);
				strel.showProgress(false);

				// Apply morphological operation, and display result
				ImageProcessor image2 = op.getOperation().apply(image, strel);
				demoImage.setProcessor(image2);
				demoImage.updateImage();

				// Compute volume of result
				vol = GrayscaleGranulometry.imageVolume(image2);
				volumes[i+1] = vol;

				// Update result table
				volumeTable.addValue(varNames[i+1], vol);
			}

			// Computes the granulometric curve
			double[] granulo = GrayscaleGranulometry.derivate(volumes);
			
			// stores the granulometric curve
			granuloTable.incrementCounter();
			granuloTable.addLabel(fileList[iImg].getName());
			for (int i = 0; i < nSteps; i++) {
				granuloTable.addValue(varNames[i+1], granulo[i]);
			}
		}

		// Compute basic stats
		ResultsTable statsTable = GrayscaleGranulometry.granuloStats(granuloTable);
		
		// Close preview image
		demoImage.changes = false;
		demoImage.close();

		// return the created array
		return new Object[]{
				"Volumes", volumeTable, 
				"Granulo", granuloTable, 
				"Stats", statsTable};
	}
	
	private void plotVolumetryCurves(ResultsTable volumeTable, String title, String unitName) {
		
		// Size of the table
		int nCols = volumeTable.getLastColumn() + 1;
		int nRows = volumeTable.getCounter();

		// Get var names and deduces strel sizes
		double[] x = new double[nCols];
		for (int i = 0; i < nCols; i++) {
			x[i] = Double.valueOf(volumeTable.getColumnHeading(i));
		}
		double xMax = x[nCols-1];
		
		// Determines the max value for y
		double yMax = 0;
		for (int i = 0; i < nRows; i++) {
			for (int j = 0; j < nCols; j++) {
				yMax = Math.max(yMax, volumeTable.getValueAsDouble(j, i));
			}
		}
		
		// create new empty plot
		Plot plot = new Plot(title, "Strel Diameter (" + unitName + ")",
				"Sum of gray levels", x, new double[nCols]);

		// set up plot
		plot.setLimits(0, xMax, 0, yMax);
		
		// Add each data row
		double[] row = new double[nCols];
		for (int r = 0; r < nRows; r++) {
			// Extract current row
			for (int c = 0; c < nCols; c++) {
				row[c] = volumeTable.getValueAsDouble(c, r);
			}
			
			plot.addPoints(x, row, Plot.LINE);
		}
		
		// Display in new window
		plot.show();
	}

	private void plotGranulometryCurves(ResultsTable granuloTable, String title, String unitName) {
		
		// Size of the table
		int nCols = granuloTable.getLastColumn() + 1;
		int nRows = granuloTable.getCounter();

		// Get var names and deduces strel sizes
		double[] x = new double[nCols];
		for (int i = 0; i < nCols; i++) {
			x[i] = Double.valueOf(granuloTable.getColumnHeading(i));
		}
		double xMax = x[nCols-1];
		
		// Determines the max value for y
		double yMax = 0;
		for (int i = 0; i < nRows; i++) {
			for (int j = 0; j < nCols; j++) {
				yMax = Math.max(yMax, granuloTable.getValueAsDouble(j, i));
			}
		}
		
		// create new empty plot
		Plot plot = new Plot(title, "Strel Diameter (" + unitName + ")",
				"Grayscale Variation (%)", x, new double[nCols]);

		// set up plot
		plot.setLimits(0, xMax, 0, yMax);
		
		// Add each data row
		double[] row = new double[nCols];
		for (int r = 0; r < nRows; r++) {
			// Extract current row
			for (int c = 0; c < nCols; c++) {
				row[c] = granuloTable.getValueAsDouble(c, r);
			}
			
			plot.addPoints(x, row, Plot.LINE);
		}
		
		// Display in new window
		plot.show();
	}
	
	
	private void saveSummaryFile(String fileName, File[] fileList, Operation op, 
			Strel.Shape shape, int diamMax, int step, Enhancement enhanceType, double resol,
			String unitName) {
		
		PrintWriter writer;
		try {
			writer = new PrintWriter(new BufferedWriter(new FileWriter(fileName)));
		} catch(IOException ex) {
			throw new RuntimeException("Could not open file: " + fileName, ex);
		}

		writer.println("Results of Grayscale Granulometry Image Texture Analysis");
		writer.println("---------");
		writer.println();
		
		writer.println("Operation Type:      " + op.toString());
		writer.println("Structuring Element: " + shape.toString());
		writer.println("Max. Diameter:       " + diamMax);
		writer.println("Diameter Step:       " + step);
		writer.println();
		writer.println("Contrast Enhancement: " + enhanceType);
		writer.println();
		writer.println("Spatial resolution:  " + resol + " " + unitName + "/pixel");
		writer.println();
		
		writer.println("List of image files:");
		for (int i = 0; i < fileList.length; i++) {
			writer.println(fileList[i].getName());	
		}

		writer.println();
		writer.println("Analysis date: " + new Date());
		
		writer.close();
	}

	private void saveResultsTable(String fileName, ResultsTable table) {
		PrintWriter writer;
		try {
			writer = new PrintWriter(new BufferedWriter(new FileWriter(fileName)));
		} catch(IOException ex) {
			throw new RuntimeException("Could not open file: " + fileName, ex);
		}

		// Size of the table
		int nCols = table.getLastColumn() + 1;
		int nRows = table.getCounter();
		
		// Write header name of each column 
		writer.print("name");
		for (int i = 0; i < nCols; i++) {
			writer.print("\t" + table.getColumnHeading(i));
		}
		writer.println();
		
		// Write header name of each column
		for (int r = 0; r < nRows; r++) {
			
			writer.print(table.getLabel(r));
			for (int c = 0; c < nCols; c++) {
				double val = table.getValueAsDouble(c, r);
				String str = String.format(Locale.US, "%7.4f", val);
				writer.print("\t" + str);
			}
			
			writer.println();
		}
		
		// Closes the file 
		writer.close();
	}

}
