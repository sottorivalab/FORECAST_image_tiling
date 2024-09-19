inResolution=0.11
outResolution=0.22099
resLevel=1
subBlockSize=(500 500)
curve=(0 1 1)
sharpSettings=(2.5 2)

thumbWidth=1024
ss1Magnification=1.25

imagePATH="../../images"
tilePATH="../../tiles"

baseDir=$(dirname "$0")

jarPATH="$baseDir/jar"
codePATH="$baseDir/class"
openCVPath="$baseDir/3rdparty/OpenCV-4.1.2/build/lib"

CLASSPATH="$CLASSPATH":"$jarPATH/CZIReader-1.7.3.jar:$jarPATH/bioformats_package.jar:$jarPATH/opencv-412.jar:$codePATH"

for imageFile in "$imagePATH"/*.czi; do
    imageFileName=$(basename "$imageFile")
    imageTilePATH="${tilePATH}/${imageFileName}"
    mkdir -p "$imageTilePATH"
    java -Djava.awt.headless=true -Djava.library.path="$openCVPath" GenerateCZITiles -r $resLevel -s ${subBlockSize[@]} -p -i $inResolution -o $outResolution -u ${sharpSettings[@]} -c ${curve[@]} -f "$imageTilePATH" "$imageFile"
    java -Djava.awt.headless=true GenerateCZITileMetadata -r $resLevel -i $inResolution -o $outResolution -w $thumbWidth -m $ss1Magnification -f "$imageTilePATH" "$imageFile"
    python "$baseDir"/src/writeParamP.py "$imageTilePATH"/param.txt "$imageTilePATH"/param.p
    rm "$imageTilePATH"/param.txt
done
