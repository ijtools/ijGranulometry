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
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ijt.analysis.granulometry.GrayscaleGranulometry.Operation;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;

/**
 * 
 */

/**
 * Plugin for computing granulometric curve from a gray level image, by 
 * specifying the diameter range of the structuring element.
 * 
 * Note that depending on structuring element, some diameters may not be
 * available (eg, disk strels require odd diameter).
 *   
 * @author David Legland
 *
 */
public class Grayscale_Granulometry_By_Diameter implements PlugIn
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
		gd.addChoice("Shape of Element", Strel.Shape.getAllLabels(), 
				Strel.Shape.SQUARE.toString());
		gd.addNumericField("Diameter Max. (in pixels)", 51, 0);
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
		Operation op 		= Operation.fromLabel(gd.getNextChoice());
		Strel.Shape shape 	= Strel.Shape.fromLabel(gd.getNextChoice());
		int diamMax 		= (int) gd.getNextNumber();		
		int step 			= (int) gd.getNextNumber();		
		double resol 		= gd.getNextNumber();
		String unitName 	= gd.getNextString();
		boolean displayVolumeCurve = gd.getNextBoolean();

		// Do some checkup on user inputs
		if (shape == Strel.Shape.DIAMOND)
		{
			if (diamMax % 2 == 0) 
			{
				IJ.error("Diamond shapes require odd diameters");
				return;
			}
			if (step % 2 == 1) 
			{
				IJ.error("Diamond shapes require even diameter steps");
				return;
			}
		}
		if (Double.isNaN(resol)) 
		{
			IJ.error("Parsing Error", "Could not interpret the resolution input");
			return;
		}
		
		ResultsTable volumeTable = computeVolumeCurve(image, op.getOperation(), shape, diamMax, step,
				resol, unitName);

		// Display volume curve and table if necessary
		if (displayVolumeCurve)
		{
			// Display table
			String title = String.format(Locale.ENGLISH,
					"Volume Curve of %s (operation=%s, shape=%s, diameterMax=%d, step=%d)",
					image.getShortTitle(), op, shape, diamMax, step);
			volumeTable.show(title);
			
			// Display curve
			double[] xi = volumeTable.getColumnAsDoubles(0);
			double[] yi = volumeTable.getColumnAsDoubles(1);
			plotVolumeCurve(xi, yi, title, unitName);
		}
		
		ResultsTable granulo = GrayscaleGranulometry.derivate(volumeTable);
		
		String title = String.format(Locale.ENGLISH,
				"Granulometry of %s (operation=%s, shape=%s, diameter=%d, step=%d)",
				image.getShortTitle(), op, shape, diamMax, step);
		granulo.show(title);
		
		// plot the granulometric curve
		double[] xi = granulo.getColumnAsDoubles(0);
		double[] yi = granulo.getColumnAsDoubles(1);
		plotGranulo(xi, yi, title, unitName);
	}

	
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
		Plot plot = new Plot(title, "Strel Diameter (" + unitName + ")",
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
		Plot plot = new Plot(title, "Strel Diameter (" + unitName + ")",
				"Image Total Intensity", x, y);

		// set up plot
		plot.setLimits(0, xMax, 0, yMax);
		
		// Display in new window
		plot.show();			
	}
	
	/**
	 * Compute granulometric curve on input image, without any display, using spatial
	 * calibration of image.
	 */
	public ResultsTable computeVolumeCurve(ImagePlus imp, Morphology.Operation op, 
			Strel.Shape shape, int diamMax, int step, double resol, String unitName) 
	{
		// Ensure input image is Gray 8
		ImageProcessor image = imp.getProcessor();
		if (!(image instanceof ByteProcessor)) 
		{
			image = image.convertToByte(true);
		}

		int nSteps = diamMax / step;
		
		double vol = GrayscaleGranulometry.imageVolume(image);

		ResultsTable table = new ResultsTable();
		
		int diam = 1;
		table.incrementCounter();
		table.addValue("Diameter", diam);
		table.addValue("Volume", vol);
		
		for (int i = 0; i < nSteps; i++) 
		{
			diam += step;
			double diam2 = diam * resol;
			
			showDiameterProgression(diam2, unitName, i, nSteps);
			
			Strel strel = shape.fromDiameter(diam);
			strel.showProgress(false);
			
			ImageProcessor image2 = op.apply(image, strel);
			imp.setProcessor(image2);
			imp.updateImage();
			
			vol = GrayscaleGranulometry.imageVolume(image2);
			
			table.incrementCounter();
			table.addValue("Diameter", diam2);
			table.addValue("Volume", vol);
		}
		
		// restore correct display 
		imp.setProcessor(image);
		imp.updateImage();
		
		// return the created array
		return table;
	}

	private void showDiameterProgression(double currentDiameter, String unitName, int i, int iMax)
	{
		String diamString = String.format(Locale.ENGLISH, "%7.2f",
				currentDiameter);
		if (unitName != null && !unitName.isEmpty()) 
		{
			diamString = diamString.concat(" " + unitName);
		}
		IJ.showStatus("Diameter: " + diamString + " (" + i + "/" + iMax + ")");
	}
}
