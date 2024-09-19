CZI Image Tiling

A program for generating tiles in CWS format from Zeiss CZI images. Uses CZI Reader, a modified version of BioFormats' ZeissCZIReader to extract the image data.

See process_images.sh for usage.

---

Dependencies:

Main java code requires Java 7 or later, please install an appropriate version of JRE/JDK if not already on your system.

For image sharpening operations, this program uses OpenCV, for which Version 4.1.2 is recommended. This can be built from source using the source code provided in "3rdparty/OpenCV-4.1.2". Build instructions can be found within the OpenCV docs, or at this link below:

https://docs.opencv.org/4.x/d0/d3d/tutorial_general_install.html#tutorial_general_install_sources_2

To also build the associated Java JAR file used by this program, please ensure that you have Apache Ant and JDK 7 (or later) installed. If OpenCV cannot find them, ensure they are included on your PATH environment variable. A pre-built version of the JAR is also provided in the "jar" folder.

If you wish to instead use a different version of OpenCV installed on your system, please update the "openCVPath" variable in process_images.sh to point to the folder where the relevant .so/.dll files are located. Also ensure that the associated JAR file is included on your CLASSPATH. You may also need to update the "System.loadLibrary" line in GenerateCZITiles.java to the correct version of opencv_java and recompile the class file.
