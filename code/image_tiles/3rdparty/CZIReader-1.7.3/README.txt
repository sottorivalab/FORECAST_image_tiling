CZI Reader: A custom CZI Reader built upon the implementation within the BioFormats library. 

At the time this was written, the BioFormats implementation could only load data from individual image series, or "scenes", within the CZI file, and could only do that using the coordinates relative to that particular series. As a result it was not possible to load data from a specific region of the whole slide, despite the BioFormats having all of the information to do so.

Thus, this reader uses a version of the ZeissCZIReader.java from BioFormats, modified to retain the (x, y) coordinate positions of each image series. Series positions are stored in a list of objects of type ExtendedCoreMetadata, which is also introduced in this package.

CZIReader.java provides the boilerplate code to load and assemble image data, potentially originating from multiple individual scenes, into a combined image, using coordinates relative to the whole slide image. If no image data exists for a particular region, it is rended as white (0xFFFFFF).

Provided is a NetBeans project which can be used to build the library.

For more information on the functions available, consult the included javadoc.
