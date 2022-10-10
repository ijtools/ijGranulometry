package ijt.analysis.granulometry;

import static org.junit.Assert.*;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ijt.analysis.granulometry.GrayscaleGranulometry.Operation;
import inra.ijpb.morphology.Strel;

import java.io.File;

import org.junit.Test;


public class GrayscaleGranulometryTest 
{	
	@Test
	public void testRadiusGranulometry_euroCoins()
	{
		ImagePlus imagePlus = IJ.openImage(getClass().getResource("/images/euroCoins_gray8.png").getFile());
		ImageProcessor image = imagePlus.getProcessor();
		
		ResultsTable res = GrayscaleGranulometry.radiusGranulometry(image,
				Operation.CLOSING, Strel.Shape.SQUARE, 50, 3);
		assertEquals(17, res.getCounter());
	}
	
	@Test
	public void testListTiffFiles()
	{
		File parent = new File(GrayscaleGranulometryTest.class.getResource("/vtt2010").getFile());
		
		File[] files = parent.listFiles();
		assertEquals(10, files.length);
	}

	@Test
	public void testListTiffGranulometry()
	{
		File parent = new File(GrayscaleGranulometryTest.class.getResource("/vtt2010").getFile());
		File[] fileList = parent.listFiles();
		
		ResultsTable res = GrayscaleGranulometry.diameterGranulometry(fileList,
				Operation.CLOSING, Strel.Shape.SQUARE, 15, 3);
		
		assertEquals(10, res.getCounter());
	}
	
}
