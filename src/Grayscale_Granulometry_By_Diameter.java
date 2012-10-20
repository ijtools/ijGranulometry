import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ijt.filter.morphology.Morphology;
import ijt.filter.morphology.Strel;
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
		gd.addChoice("Element", Strel.Shape.getAllLabels(), 
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

		// show result
		ResultsTable table = (ResultsTable ) res[1]; 

		ResultsTable granulo = derivate(table);
		granulo.show("Granulometry of "  + image.getShortTitle());
		
//		double[] vi = granulo.getColumnAsDoubles(0);
		double[] xi = granulo.getColumnAsDoubles(0);
		double[] yi = granulo.getColumnAsDoubles(1);
		
		plotGranulo(xi, yi, "Granulometry of " + image.getShortTitle());
	}

//	private void plotTableColumn(ResultsTable table, int index, String title) {
//		
//		int nr = table.getCounter();
//		double[] x = table.getColumnAsDoubles(0);
//		double[] y = table.getColumnAsDoubles(1);
//		double xMax = x[x.length-1];
//		double yMax = 0;
//		for (int i = 0; i < nr; i++) {
//			yMax = Math.max(yMax, y[i]);
//		}
//		
//		// create plot with default line
//		Plot plot = new Plot(title, "Strel Diameter", "Image Volume", x, y);
//		
//		// set up plot
//		plot.setLimits(0, xMax, 0, yMax);
//		
//		// Display in new window
//		plot.show();			
//
//	}
	
//	private void plotGranulo(double[] values) {
//		
//		int nr = values.length;
//		double[] x = new double[nr];
//		double yMax = 0;
//		for (int i = 0; i < nr; i++) {
//			x[i] = i;
//			yMax = Math.max(yMax, values[i]);
//		}
//		
//		// create plot with default line
//		Plot plot = new Plot("Granulometric Curve", "Strel Diameter", "Grayscale Variation (%)", 
//				x, values);
//		
//		// set up plot
//		plot.setLimits(0, nr, 0, yMax);
//		
//		// Display in new window
//		plot.show();			
//
//	}
	
	
	private void plotGranulo(double[] x, double[] y, String title) {
		
		int nr = x.length;
		double xMax = x[nr-1];
		double yMax = 0;
		for (int i = 0; i < nr; i++) {
			yMax = Math.max(yMax, y[i]);
		}
		
		// create plot with default line
		Plot plot = new Plot(title, "Strel Diameter",
				"Grayscale Variation (%)", x, y);

		// set up plot
		plot.setLimits(0, xMax, 0, yMax);
		
		// Display in new window
		plot.show();			
	}

	public Object[] exec(ImagePlus imp, Morphology.Operation op, 
			Strel.Shape shape, int diamMax, int step) {
		
		ImageProcessor image = imp.getProcessor();
		int nSteps = diamMax / step;
		
		double[] volumes = new double[nSteps + 1];
		
		double vol = imageVolume(image);
		volumes[0] = vol;
		
		ResultsTable table = new ResultsTable();
		table.incrementCounter();
		table.addValue("Size", 0);
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
				
			vol = imageVolume(image2);
			volumes[i+1] = vol;
			
			table.incrementCounter();
			table.addValue("Size", diam);
			table.addValue("Volume", vol);
		}
		
		imp.setProcessor(image);
		imp.updateImage();
		
		// return the created array
		return new Object[]{"Granulometry", table};
	}

	public ResultsTable granulometricCurve(ImageProcessor image, Morphology.Operation op, 
			Strel.Shape shape, int diamMax, int step) {
		
		int nSteps = diamMax / step;
		
		double vol = imageVolume(image);

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
			
			vol = imageVolume(image2);
			
			table.incrementCounter();
			table.addValue("Diameter", diam);
			table.addValue("Volume", vol);
		}
		
		return table;
	}

	private double imageVolume(ImageProcessor image) {
		double res = 0;;
		double resy = 0;
		int width = image.getWidth();
		int height = image.getHeight();
		
		for (int y = 0; y < height; y++) {
			resy = 0;
			for (int x = 0; x < width; x++) {
				resy += image.getf(x, y);
			}
			res += resy;
		}
		return res;
	}

	/**
	 * Computes derivative of the second column of the table
	 */
	private ResultsTable derivate(ResultsTable table) {
		int n = table.getCounter();
		
		double[] xres = new double[n-1];
		double[] yres = new double[n-1];
		
		// extract initial and final values
		double v0 = table.getValueAsDouble(1, 0);
		double vf = table.getValueAsDouble(1, n-1);

		ResultsTable result = new ResultsTable();
		
		// compute normalized derivative
		double v1 = v0;
		for (int i = 1; i < n-1; i++) {
			xres[i] = table.getValueAsDouble(0, i);
			double v2 = table.getValueAsDouble(1, i);
			yres[i] = 100 * (v2 - v1) / (vf - v0);
			v1 = v2;
			
			result.incrementCounter();
			result.addValue("Diameter", table.getValueAsDouble(0, i));
			result.addValue("Variation", yres[i]);
		}
		
		return result;
	}

//	private double[] derivate(double[] input) {
//		int n = input.length;
//		double[] result = new double[n];
//		
//		// extract initial and final values
//		double v0 = input[0];
//		double vf = input[n-1];
//		
//		// first result is set to 0 by definition
//		result[0] = 0;
//		
//		// compute normalized derivative
//		double v1 = v0;
//		for (int i = 1; i < n; i++) {
//			double v2 = input[i];
//			result[i] = 100 * (v2 - v1) / (vf - v0);
//			v1 = v2;
//		}
//		
//		return result;
//	}
}
