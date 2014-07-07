import java.util.Locale;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ijt.filter.morphology.Morphology;
import ijt.filter.morphology.Strel;
import ijt.analysis.granulometry.GrayscaleGranulometry;
import ijt.analysis.granulometry.GrayscaleGranulometry.Operation;

/**
 * 
 */

/**
 * @author David Legland
 *
 */
public class Grayscale_Granulometry_By_Diameter implements PlugIn {

	
	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		ImagePlus image = WindowManager.getCurrentImage();
		if (image == null) {
			IJ.error("No image", "Need at least one image to work");
			return;
		}
		
		// create the dialog
		GenericDialog gd = new GenericDialog("Morphological Filter");
		
		gd.addChoice("Operation", Operation.getAllLabels(), 
				Operation.CLOSING.toString());
		gd.addChoice("Shape of Element", Strel.Shape.getAllLabels(), 
				Strel.Shape.SQUARE.toString());
		gd.addNumericField("Diameter Max. (in pixels)", 51, 0);
		gd.addNumericField("Step (in pixels)", 1, 0);
		
		// Could also add an option for the type of operation
		gd.showDialog();
		
		if (gd.wasCanceled())
			return;
		
		// extract chosen parameters
		Operation op = Operation.fromLabel(gd.getNextChoice());
		Strel.Shape shape = Strel.Shape.fromLabel(gd.getNextChoice());
		int diamMax = (int) gd.getNextNumber();		
		int step 	= (int) gd.getNextNumber();		
	
		// Execute core of the plugin
		Object[] res = exec(image, op.getOperation(), shape, diamMax, step);
		if (res == null)
			return;

		ResultsTable table = (ResultsTable) res[1];
		
		ResultsTable granulo = GrayscaleGranulometry.derivate(table);
		
		String title = String.format(Locale.ENGLISH,
				"Granulometry of %s (operation=%s, shape=%s, diameter=%d, step=%d)",
				image.getShortTitle(), op, shape, diamMax, step);

		granulo.show(title);
		
		double[] xi = granulo.getColumnAsDoubles(0);
		double[] yi = granulo.getColumnAsDoubles(1);
		plotGranulo(xi, yi, title);
	}

	
	private void plotGranulo(double[] x, double[] y, String title) {
		
		int nr = x.length;
		double xMax = x[nr-1];
		double yMax = 0;
		for (int i = 0; i < nr; i++) {
			yMax = Math.max(yMax, y[i]);
		}
		
		// create plot with default line
		Plot plot = new Plot(title, "Strel Diameter (pixels)",
				"Grayscale Variation (%)", x, y);
	
		// set up plot
		plot.setLimits(0, xMax, 0, yMax);
		
		// Display in new window
		plot.show();			
	}

	/**
	 * Compute granulometric curve on current image, and display progression in current frame. 
	 */
	public Object[] exec(ImagePlus imp, Morphology.Operation op, 
			Strel.Shape shape, int diamMax, int step) {
		
		// Extract image processor, make sure it is Gray8
		ImageProcessor image = imp.getProcessor();
		if (image instanceof ShortProcessor) {
			image = image.convertToByte(true);
		}

		int nSteps = diamMax / step;
		
		double vol = GrayscaleGranulometry.imageVolume(image);
		
		ResultsTable table = new ResultsTable();
		table.incrementCounter();
		table.addValue("Diameter", 0);
		table.addValue("Volume", vol);
		
		int diam = 1;
		for (int i = 0; i < nSteps; i++) {
			diam += step;
			
			IJ.showStatus("Diameter " + diam + "(" + i + "/" + nSteps + ")");
			
			Strel strel = shape.fromDiameter(diam);
			strel.showProgress(false);
			
			ImageProcessor image2 = op.apply(image, strel);
			imp.setProcessor(image2);
			imp.updateImage();
				
			vol = GrayscaleGranulometry.imageVolume(image2);
			
			table.incrementCounter();
			table.addValue("Diameter", diam);
			table.addValue("Volume", vol);
		}
		
		imp.setProcessor(image);
		imp.updateImage();
		
		// return the created array
		return new Object[]{"Granulometry", table};
	}

	/**
	 * Compute granulometric curve on current image, without any display, and
	 * return directly the data table.
	 */
	public ResultsTable granulometricCurve(ImageProcessor image, Morphology.Operation op, 
			Strel.Shape shape, int diamMax, int step) {
		
		// Ensure input image is Gray 8
		if (image instanceof ShortProcessor) {
			image = image.convertToByte(true);
		}

		int nSteps = diamMax / step;
		
		double vol = GrayscaleGranulometry.imageVolume(image);

		ResultsTable table = new ResultsTable();
		
		int diam = 1;
		table.incrementCounter();
		table.addValue("Diameter", diam);
		table.addValue("Volume", vol);
		
		for (int i = 0; i < nSteps; i++) {
			diam += step;
			
			IJ.showStatus("Diameter " + diam + "(" + i + "/" + nSteps + ")");
			
			Strel strel = shape.fromDiameter(diam);
			strel.showProgress(false);
			
			ImageProcessor image2 = op.apply(image, strel);
			
			vol = GrayscaleGranulometry.imageVolume(image2);
			
			table.incrementCounter();
			table.addValue("Diameter", diam);
			table.addValue("Volume", vol);
		}
		
		return table;
	}

}
