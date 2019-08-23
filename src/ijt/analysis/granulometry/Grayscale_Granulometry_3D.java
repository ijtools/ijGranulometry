package ijt.analysis.granulometry;

import java.util.Locale;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ijt.analysis.granulometry.GrayscaleGranulometry.Operation;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel3D;
import inra.ijpb.util.IJUtils;

/**
 * Plugin for computing granulometric curve from a gray level 3D image, by 
 * specifying the radius range of the structuring element.
 * 
 * @author David Legland
 *
 */
public class Grayscale_Granulometry_3D implements PlugIn 
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
		if (image.getStackSize() <= 1)
		{
			IJ.error("Requires Stack", "Requires a stack image to work");
			return;
		}
		
		// create the dialog
		GenericDialog gd = new GenericDialog("Grayscale Granulometry");
		
		gd.addChoice("Operation", Operation.getAllLabels(), 
				Operation.CLOSING.toString());
		gd.addChoice("Element", Strel3D.Shape.getAllLabels(), 
				Strel3D.Shape.CUBE.toString());
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
		Operation op 		= Operation.fromLabel(gd.getNextChoice());
		Strel3D.Shape shape	= Strel3D.Shape.fromLabel(gd.getNextChoice());
		int radiusMax 		= (int) gd.getNextNumber();		
		int step 			= (int) gd.getNextNumber();		
		double resol 		= gd.getNextNumber();
		String unitName 	= gd.getNextString();
		boolean displayVolumeCurve = gd.getNextBoolean();
	
		// Do some checkup on user inputs
		if (Double.isNaN(resol)) 
		{
			IJ.error("Parsing Error", "Could not interpret the resolution input");
			return;
		}
		
		// Execute core of the plugin
		long tic = System.nanoTime();
		ResultsTable volumeTable = computeVolumeCurve(image, op.getOperation(), shape, radiusMax, step, 
				resol, unitName);
		long toc = System.nanoTime();
		double timeInMilliSecs = (toc - tic) / 1000000.0;
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
			double[] xi = volumeTable.getColumnAsDoubles(1);
			double[] yi = volumeTable.getColumnAsDoubles(2);
			plotVolumeCurve(xi, yi, title, unitName);
		}
		
		// Compute granulometry curve from volume curve
		ResultsTable granulo = GrayscaleGranulometry.derivate(volumeTable, 1, 2);
		
		// Display resulting granulometry curve
		String title = String.format(Locale.ENGLISH,
				"Granulometry of %s (operation=%s, shape=%s, radiusMax=%d, step=%d)",
				image.getShortTitle(), op, shape, radiusMax, step);
		granulo.show(title);
		
		// plot the granulometric curve
		double[] xi = granulo.getColumnAsDoubles(0);
		double[] yi = granulo.getColumnAsDoubles(1);
		plotGranulo(xi, yi, title, unitName);
		
		IJUtils.showElapsedTime("3D granulometry", timeInMilliSecs, image);
	}
	
    /**
     * Displays the granulometric curve and adds some decorations.
     * 
     * @param x
     *            the array of values for sizes
     * @param y
     *            the array of values for size distribution
     * @param title
     *            the title of the graph
     * @param unitName
     *            unit name (for legend)
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
     * @param x
     *            the array of values for sizes
     * @param y
     *            the array of values for size distribution
     * @param title
     *            the title of the graph
     * @param unitName
     *            unit name (for legend)
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
	
	public ResultsTable computeVolumeCurve(ImagePlus imp, Morphology.Operation op, 
			Strel3D.Shape shape, int radiusMax, int step, double resol, String unitName) 
	{
		// Extract image processor, make sure it is Gray8
		ImageStack image = imp.getStack();
//		if (image instanceof ShortProcessor) 
//		{
//			image = image.convertToByte(true);
//		}

		IJ.log("init");
		int nSteps = radiusMax / step;
		double[] volumes = new double[nSteps + 1];
		
		double vol = GrayscaleGranulometry.imageVolume(image);
		volumes[0] = vol;
		
		ResultsTable table = new ResultsTable();
		table.incrementCounter();
		table.addValue("Radius", 0);
		table.addValue("Diameter", 0);
		table.addValue("Volume", vol);
		
		int radius = 0;
		for (int i = 0; i < nSteps; i++) 
		{
			radius += step;
			IJ.log("granulo step " + i + "/" + nSteps + ": radius= " + radius);
			
			double radius2 = radius * resol;
			double diam2 = (2 * radius + 1) * resol;
			
			showRadiusProgression(radius2, unitName, i, nSteps);
			
			Strel3D strel = shape.fromRadius(radius);
			strel.showProgress(false);
			
			ImageStack image2 = op.apply(image, strel);
//			imp.setProcessor(image2);
//			imp.updateImage();
				
			vol = GrayscaleGranulometry.imageVolume(image2);
			volumes[i+1] = vol;
			
			table.incrementCounter();
			table.addValue("Radius", radius2);
			table.addValue("Diameter", diam2);
			table.addValue("Volume", vol);
		}
		
//		// restore correct display 
//		imp.setProcessor(image);
//		imp.updateImage();
		
		// return the created array
		return table;
	}

	private void showRadiusProgression(double currentRadius, String unitName, int i, int iMax) 
	{
		String radiusString = String.format(Locale.ENGLISH, "%7.2f", currentRadius);
		if (unitName != null && !unitName.isEmpty()) 
		{
			radiusString = radiusString.concat(" " + unitName);
		}
		IJ.showStatus("Radius: " + radiusString + " (" + i + "/" + iMax + ")");
	}
}
