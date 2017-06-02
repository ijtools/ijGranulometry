package ijt.analysis.granulometry;

import java.util.Locale;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import ijt.analysis.granulometry.GrayscaleGranulometry;
import ijt.analysis.granulometry.GrayscaleGranulometry.Operation;


/**
 * 
 */

/**
 * Plugin for computing granulometric curve from a gray level image, by 
 * specifying the radius range of the structuring element.
 * 
 * @author David Legland
 *
 */
public class Grayscale_Granulometry_By_Radius implements PlugIn 
{
	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg) 
	{
		// Get current open image
		ImagePlus image = WindowManager.getCurrentImage();
		if (image == null) 
		{
			IJ.error("No image", "Need at least one image to work");
			return;
		}
		
		// create the dialog
		GenericDialog gd = new GenericDialog("Grayscale Granulometry");
		
		gd.addChoice("Operation", Operation.getAllLabels(), 
				Operation.CLOSING.toString());
		gd.addChoice("Element", Strel.Shape.getAllLabels(), 
				Strel.Shape.SQUARE.toString());
		gd.addNumericField("Radius Max. (in pixels)", 25, 0);
		gd.addNumericField("Step (in pixels)", 1, 0);
		// add psb to specify spatial calibration
		Calibration calib = image.getCalibration();
		gd.addNumericField("Spatial_Calibration", calib.pixelWidth, 3);
		gd.addStringField("Calibration_Unit", calib.getUnit());
		gd.addCheckbox("Display Volume Curve", false);

		// Display dialog and wait for user input
		gd.showDialog();
		if (gd.wasCanceled())
		{
			return;
		}
		
		// extract chosen parameters
		Operation op = Operation.fromLabel(gd.getNextChoice());
		Strel.Shape shape = Strel.Shape.fromLabel(gd.getNextChoice());
		int radiusMax = (int) gd.getNextNumber();		
		int step 	= (int) gd.getNextNumber();		
		double resol = gd.getNextNumber();
		String unitName = gd.getNextString();
		boolean displayVolumeCurve = gd.getNextBoolean();
	
		// Do some checkup on user inputs
		if (Double.isNaN(resol)) 
		{
			IJ.error("Parsing Error", "Could not interpret the resolution input");
			return;
		}
		
		// Execute core of the plugin
		ResultsTable volumeTable = computeVolumeCurve(image, op.getOperation(), shape, radiusMax, step, 
				resol, unitName);
		if (volumeTable == null)
			return;

		// Display volume curve and table if necessary
		if (displayVolumeCurve)
		{
			// Display table
			String title = String.format(Locale.ENGLISH,
					"Volume Curve of %s (operation=%s, shape=%s, radius=%d, step=%d)",
					image.getShortTitle(), op, shape, radiusMax, step);
			volumeTable.show(title);
			
			// Display curve
			double[] xi = volumeTable.getColumnAsDoubles(0);
			double[] yi = volumeTable.getColumnAsDoubles(1);
			plotVolumeCurve(xi, yi, title, unitName);
		}
		
		// Compute granulometry curve from volume curve
		ResultsTable granulo = GrayscaleGranulometry.derivate(volumeTable);
		
		// Display resulting granulometry curve
		String title = String.format(Locale.ENGLISH,
				"Granulometry of %s (operation=%s, shape=%s, radius=%d, step=%d)",
				image.getShortTitle(), op, shape, radiusMax, step);
		granulo.show(title);
		
		// plot the granulometric curve
		double[] xi = granulo.getColumnAsDoubles(0);
		double[] yi = granulo.getColumnAsDoubles(1);
		plotGranulo(xi, yi, title, unitName);
	}
	
	/**
	 * Displays the granulometric curve and adds some decorations.
	 *  
	 * @param x the array of values for sizes
	 * @param y the array of values for size distribution
	 * @param title the title of the graph
	 * @param unitName unit name (for legend)
	 */
	private void plotGranulo(double[] x, double[] y, String title, String unitName) 
	{
		int nr = x.length;
		double xMax = x[nr-1];
		double yMax = 0;
		for (int i = 0; i < nr; i++) 
		{
			yMax = Math.max(yMax, y[i]);
		}
		
		// create plot with default line
		Plot plot = new Plot(title, "Strel Radius (" + unitName + ")",
				"Grayscale Variation (%)", x, y);

		// set up plot
		plot.setLimits(0, xMax, 0, yMax);
		
		// Display in new window
		plot.show();			
	}

	/**
	 * Displays the image volume curve and adds some decorations.
	 *  
	 * @param x the array of values for sizes
	 * @param y the array of values for size distribution
	 * @param title the title of the graph
	 * @param unitName unit name (for legend)
	 */
	private void plotVolumeCurve(double[] x, double[] y, String title, String unitName) 
	{
		int nr = x.length;
		double xMax = x[nr-1];
		double yMax = 0;
		for (int i = 0; i < nr; i++) 
		{
			yMax = Math.max(yMax, y[i]);
		}
		
		// create plot with default line
		Plot plot = new Plot(title, "Strel Radius (" + unitName + ")",
				"Image Total Intensity", x, y);

		// set up plot
		plot.setLimits(0, xMax, 0, yMax);
		
		// Display in new window
		plot.show();			
	}
	
	public ResultsTable computeVolumeCurve(ImagePlus imp, Morphology.Operation op, 
			Strel.Shape shape, int diamMax, int step, double resol, String unitName) 
	{
		// Extract image processor, make sure it is Gray8
		ImageProcessor image = imp.getProcessor();
		if (image instanceof ShortProcessor) 
		{
			image = image.convertToByte(true);
		}

		int nSteps = diamMax / step;
		
		double[] volumes = new double[nSteps + 1];
		
		double vol = GrayscaleGranulometry.imageVolume(image);
		volumes[0] = vol;
		
		ResultsTable table = new ResultsTable();
		table.incrementCounter();
		table.addValue("Radius", 0);
		table.addValue("Volume", vol);
		
		int radius = 0;
		for (int i = 0; i < nSteps; i++) 
		{
			radius += step;
			
			double radius2 = radius * resol;
			
			showRadiusProgression(radius2, unitName, i, nSteps);
			
			Strel strel = shape.fromRadius(radius);
			strel.showProgress(false);
			
			ImageProcessor image2 = op.apply(image, strel);
			imp.setProcessor(image2);
			imp.updateImage();
				
			vol = GrayscaleGranulometry.imageVolume(image2);
			volumes[i+1] = vol;
			
			table.incrementCounter();
			table.addValue("Radius", radius2);
			table.addValue("Volume", vol);
		}
		
		imp.setProcessor(image);
		imp.updateImage();
		
		// return the created array
		return table;
	}

	public ResultsTable granulometricCurve(ImageProcessor image, Morphology.Operation op, 
			Strel.Shape shape, int radiusMax, int step)
	{
		return granulometricCurve(image, op, shape, radiusMax, step, 1, "");
	}
	
	public ResultsTable granulometricCurve(ImageProcessor image, Morphology.Operation op, 
			Strel.Shape shape, int radiusMax, int step, double resol, String unitName) 
	{
		// Ensure input image is Gray 8
		if (image instanceof ShortProcessor) 
		{
			image = image.convertToByte(true);
		}

		int nSteps = radiusMax / step;
		
		double vol = GrayscaleGranulometry.imageVolume(image);

		ResultsTable table = new ResultsTable();
		
		int radius = 1;
		table.incrementCounter();
		table.addValue("Radius", radius);
		table.addValue("Volume", vol);
		
		for (int i = 0; i < nSteps; i++) 
		{
			radius += step;
			double radius2 = radius * resol;
			
			showRadiusProgression(radius2, unitName, i, nSteps);
			
			Strel strel = shape.fromRadius(radius);
			strel.showProgress(false);
			
			ImageProcessor image2 = op.apply(image, strel);
			
			vol = GrayscaleGranulometry.imageVolume(image2);
			
			table.incrementCounter();
			table.addValue("Radius", radius2);
			table.addValue("Volume", vol);
		}
		
		return table;
	}

	private void showRadiusProgression(double currentDiameter, String unitName,
			int i, int iMax) 
	{
		String radiusString = String.format(Locale.ENGLISH, "%7.2f",
				currentDiameter);
		if (unitName != null && !unitName.isEmpty()) 
		{
			radiusString = radiusString.concat(" " + unitName);
		}
		IJ.showStatus("Radius: " + radiusString + " (" + i + "/" + iMax + ")");
	}
}
