package ijt.analysis.granulometry;

import static org.junit.Assert.*;

import ij.measure.ResultsTable;
import ijt.analysis.granulometry.GrayscaleGranulometry.Operation;
import inra.ijpb.morphology.Strel;

import java.io.File;

import org.junit.Test;


public class GrayscaleGranulometryTest 
{	
	@Test
	public void testListTiffFiles()
	{
		File pattern = new File("./files/vtt2010");
		File[] files = pattern.listFiles();
		assertEquals(10, files.length);
	}

	@Test
	public void testListTiffGranulometry()
	{
		File pattern = new File("./files/vtt2010");
		File[] fileList = pattern.listFiles();
		
		ResultsTable res = GrayscaleGranulometry.diameterGranulometry(fileList,
				Operation.CLOSING, Strel.Shape.SQUARE, 15, 3);
		
		assertEquals(10, res.getCounter());
	}
	
}
