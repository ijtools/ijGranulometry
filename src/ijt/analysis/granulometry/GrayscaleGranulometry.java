/**
 * 
 */
package ijt.analysis.granulometry;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ijt.filter.morphology.Morphology;
import ijt.filter.morphology.Strel;

import java.io.File;

/**
 * @author David Legland
 *
 */
public class GrayscaleGranulometry {
	
	// =======================================================================
	// Enumeration for operations

	/**
	 * A pre-defined set of operations.
	 * Mainly a wrapper to a subset of operations defined in 
	 * Morphology.Operation.
	 * @see ijt.filter.morphology.Morphology.Operation
	 */
	public enum Operation {
		EROSION(Morphology.Operation.EROSION),
		DILATION(Morphology.Operation.DILATION),
		CLOSING(Morphology.Operation.CLOSING),
		OPENING(Morphology.Operation.OPENING);
		
		private Morphology.Operation op;
		
		private Operation(Morphology.Operation op) {
			this.op = op;
		}
		
		public Morphology.Operation getOperation() {
			return this.op;
		}
		
		public String toString() {
			return this.op.toString();
		}
		
		public static String[] getAllLabels(){
			int n = Operation.values().length;
			String[] result = new String[n];
			
			int i = 0;
			for (Operation op : Operation.values())
				result[i++] = op.toString();
			
			return result;
		}
		
		/**
		 * Determines the operation type from its label.
		 * @throws IllegalArgumentException if label is not recognized.
		 */
		public static Operation fromLabel(String opLabel) {
			if (opLabel != null)
				opLabel = opLabel.toLowerCase();
			for (Operation op : Operation.values()) {
				String cmp = op.toString().toLowerCase();
				if (cmp.equals(opLabel))
					return op;
			}
			throw new IllegalArgumentException("Unable to parse Operation with label: " + opLabel);
		}
	};

	
	// =======================================================================
	// methods for computing granulometries
	
	/**
	 * Computes gray scale granulometry for all image files in a given
	 * directory and returns the corresponding result table.
	 */
	public final static ResultsTable diameterGranulometry(File[] fileList,
			Operation op, Strel.Shape shape, int diamMax, int step) {
		
		int nSteps = diamMax / step;
	
		ResultsTable table = new ResultsTable();
		
		for (int i = 0; i < fileList.length; i++) {
//			System.out.println("Process file: " + fileList[i].getName());

			ImagePlus image = IJ.openImage(fileList[i].getAbsolutePath());
			ImageProcessor proc = image.getProcessor();
			
			ResultsTable granulo = diameterGranulometry(proc, op, shape, diamMax, step);
	
			table.incrementCounter();
			table.addLabel(fileList[i].getName());
			
			for (int j = 0; j < nSteps; j++) {
				table.addValue(j, granulo.getValueAsDouble(1, j));
			}
		}
		
		return table;
	}


	public final static ResultsTable diameterGranulometry(ImageProcessor image,
			Operation op, Strel.Shape shape, int diamMax, int step) {
		
		Morphology.Operation op2 = op.getOperation();
		
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
			
			ImageProcessor image2 = op2.apply(image, strel);
			
			vol = imageVolume(image2);
			
			table.incrementCounter();
			table.addValue("Diameter", diam);
			table.addValue("Volume", vol);
		}
		
		return table;
	}

	public final static ResultsTable radiusGranulometry(ImageProcessor image,
			Operation op, Strel.Shape shape, int radiusMax, int step) {
	
		Morphology.Operation op2 = op.getOperation();
		
		int nSteps = radiusMax / step;
		
		double vol = imageVolume(image);

		ResultsTable table = new ResultsTable();
		
		int radius = 1;
		table.incrementCounter();
		table.addValue("Radius", radius);
		table.addValue("Volume", vol);
		
		for (int i = 0; i < nSteps; i++) {
			radius += step;
			
			IJ.showStatus("Radius " + radius + "(" + i + "/" + nSteps + ")");
			
			Strel strel = shape.fromRadius(radius);
			strel.showProgress(false);
			
			ImageProcessor image2 = op2.apply(image, strel);
			
			vol = imageVolume(image2);
			
			table.incrementCounter();
			table.addValue("Radius", radius);
			table.addValue("Volume", vol);
		}
		
		return table;
	}
	
	// =======================================================================
	// Utility methods

	/**
	 * Computes the gray scale volume of the input image, by computing the sum
	 * of intensity value for each pixel.
	 * 
	 * @param image
	 *            a gray scale image
	 * @return the sum of pixel intensities
	 */
	public final static double imageVolume(ImageProcessor image) {
		// image size
		int width = image.getWidth();
		int height = image.getHeight();

		double resy = 0;
		double res = 0;
		
		// iterate on rows
		for (int y = 0; y < height; y++) {
			// Compute sum of grays on current row
			resy = 0;
			for (int x = 0; x < width; x++) {
				resy += image.getf(x, y);
			}
			
			// add to global result
			res += resy;
		}
		return res;
	}

	
	/**
	 * Computes derivative of the second column of the table
	 */
	public final static ResultsTable derivate(ResultsTable table) {
		// number of table entries
		int n = table.getCounter();
		
		// allocate memory
		double[] xres = new double[n-1];
		double[] yres = new double[n-1];
		
		// Name of the column containing the "size" information
		String sizeColumnName = table.getColumnHeading(0);
		
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
			result.addValue(sizeColumnName, table.getValueAsDouble(0, i));
			result.addValue("Variation", yres[i]);
		}
		
		return result;
	}

	/**
	 * Computes derivative of the input data array, and returns a new array.
	 */
	public final static double[] derivate(double[] data) {
		// number of table entries
		int n = data.length;
		
		// allocate memory
		double[] res = new double[n-1];
		
		// extract initial and final values
		double v0 = data[0];
		double vf = data[n-1];

		// compute normalized derivative
		double v1 = v0;
		for (int i = 1; i < n; i++) {
			double v2 = data[i];
			res[i-1] = 100 * (v2 - v1) / (vf - v0);
			v1 = v2;
		}
		
		return res;
	}
	
	/**
	 * Computes basic statistics for each granulometric curve given as row in
	 * the input data table, using column label to assess x values.  
	 * @param table input granulometry table
	 * @return a data table with the same number of rows, and one column by summary statistics
	 */
	public final static ResultsTable granuloStats(ResultsTable granuloTable) {
		
		// Size of the table
		int nCols = granuloTable.getLastColumn() + 1;
		int nRows = granuloTable.getCounter();

		// Get var names and deduces strel sizes
		double[] x = new double[nCols];
		for (int i = 0; i < nCols; i++) {
			x[i] = Double.valueOf(granuloTable.getColumnHeading(i));
		}

		// Compute mean of each row
		double[] means = new double[nRows];
		for (int r = 0; r < nRows; r++) {
			// Extract current row
			double accum = 0;
			for (int c = 0; c < nCols; c++) {
				accum += granuloTable.getValueAsDouble(c, r) * x[c] / 100;
			}
			
			means[r] = accum;
		}
		
		// Compute standard deviations
		double[] stds = new double[nRows];
		for (int r = 0; r < nRows; r++) {
			// Extract current row
			double accum = 0;
			for (int c = 0; c < nCols; c++) {
				double dev = (x[c] - means[r]);
				double sqd = dev * dev;
				accum += sqd * granuloTable.getValueAsDouble(c, r) / 100;
			}
			
			stds[r] = Math.sqrt(accum);
		}
		
		// Compute geometric mean
		double[] geommeans = new double[nRows];
		for (int r = 0; r < nRows; r++) {
			// Extract current row
			double accum = 0;
			for (int c = 0; c < nCols; c++) {
				double freq = granuloTable.getValueAsDouble(c, r) / 100;
				accum += Math.log(x[c]) * freq;
			}
			
			geommeans[r] = Math.exp(accum);
		}

		// Create the resulting data table

		ResultsTable results = new ResultsTable();
		
		for (int r = 0; r < nRows; r++) {
			results.incrementCounter();

			results.addLabel(granuloTable.getLabel(r));

			results.addValue("mean", means[r]);
			results.addValue("std", stds[r]);
			results.addValue("geommean", geommeans[r]);
		}

		return results;
	}
		

}
