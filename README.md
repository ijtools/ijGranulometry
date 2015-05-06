# ijGranulometry
Grayscale granulometry plugin for ImageJ. Requires MorphoLibJ plugin.

This plugins allows computation of granulometric curves computed on grayscale images 
using operators from mathematical morphology. It is well suited for texture analysis of 
greyscale images.

## Installation ##
Copy the jar file into the /plugins directory of your ImageJ installation.

The plugin relies on the MorpholLibJ library, so the latest version of MorphoLibJ is requried as well.

## Usage ##

After installation, a new "Granulometry" menu appears within the plugins menu. It allows:
* running granulometry on a single image
* running granulometry on a collection of images.

Several structuring element shapes may be chosen: square, disk, diamond, octagon, line segments with various orientations.

Several operation may be applied: erosion, dilation, closing or opening.

## known bugs and limitations ##

The "diamond" structuring element can not be defined with even diameters.
Also, the resulting granulometric curves may be more or less "spiky" due to discretization effects of the structuring element.
