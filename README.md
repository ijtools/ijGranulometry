# ijGranulometry
Grayscale granulometry plugin for ImageJ. Requires MorphoLibJ plugin.

This plugins allows computation of granulometric curves computed on grayscale images 
using operators from mathematical morphology. It is well suited for texture analysis of 
greyscale images.

## Installation ##
Copy the jar file into the /plugins directory of your ImageJ installation.

The plugin relies on the [MorphoLibJ library](https://github.com/ijpb/MorphoLibJ),
so the latest version of MorphoLibJ is required as well.

## Usage ##

After installation, a new "Granulometry" menu appears within the plugins menu. It allows:
* running granulometry on a single image
* running granulometry on a collection of images.


Several structuring element shapes may be chosen: square, disk, diamond, octagon, 
or line segments with various orientations.

Several operation may be applied: erosion, dilation, closing or opening.

The difference between the radius-based and the diameter-based granulometries rely on the 
way the structuring element is computed. Structuring elements defined from a diameter can
have a larger variety of sizes, but the symmetry of the structuring element is not 
warranted. Moreover, if can not be used for some structuring elements (eg "diamond").


## known bugs and limitations ##

The "diamond" structuring element can not be defined with even diameters.
Also, the resulting granulometric curves may be more or less "spiky" due to 
discretization effects of the structuring element.
