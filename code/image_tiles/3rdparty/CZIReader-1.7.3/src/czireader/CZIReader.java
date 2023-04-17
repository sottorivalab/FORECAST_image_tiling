/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package czireader;

import java.io.IOException;
import loci.formats.FormatException;
import loci.common.DebugTools;
import java.util.Hashtable;

/**
 *
 * @author ntrahearn
 */
public class CZIReader {

    public static void main(String[] args) {
        if (args.length < 1) {
             System.out.println("You must specify a file.");
             System.exit(1);
        }
        
        try {
            CZIReader c = new CZIReader(args[0]);
            Hashtable<String, Object> r = c.Reader.getGlobalMetadata();
            byte[] data = c.getImageRegion(7);
            for (String key : r.keySet()) {
                System.out.println(key+" "+r.get(key));
            }
            c.close();
        } catch (FormatException fe) {
            fe.printStackTrace();
            System.exit(2);
        } catch (IOException io) {
            io.printStackTrace();
            System.exit(3);
        }
    }

    private final String FileName;
    private final ZeissCZIReaderX Reader;
    private final int SceneCount;
    private final int[] ImageSize;
    private final int[] ImageOrigin;
    private final int MaxResolution;
    private final int BarcodeIndex;
    private final int PreviewIndex;
    
    public CZIReader(String fName) throws FormatException, IOException {
        DebugTools.enableLogging("ERROR");
        this.FileName = fName;
        this.Reader = new ZeissCZIReaderX();
        this.Reader.setFlattenedResolutions(false);
        this.Reader.setId(this.FileName);
        
        int sceneCount = Reader.getSeriesCount();
        int barcodeIndex = -1;
        int previewIndex = -1;
        
        this.Reader.setSeries(sceneCount-1);
        
        if (this.Reader.isThumbnailSeries()) {
            previewIndex = sceneCount-1;
            sceneCount--;
            
            this.Reader.setSeries(sceneCount-1);
            
            if (this.Reader.isThumbnailSeries()) {
                barcodeIndex = sceneCount-1;
                sceneCount--;
            }
        }
    
        this.SceneCount = sceneCount;
        this.BarcodeIndex = barcodeIndex;
        this.PreviewIndex = previewIndex;
        
        int maxRes = Integer.MIN_VALUE;
        
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        
        for (int i=0; i<SceneCount; i++) {
            this.Reader.setSeries(i);
            this.Reader.setResolution(0);
            
            int res = this.Reader.getResolutionCount();
            maxRes = Math.max(maxRes, res);
            
            int X = Reader.getPositionX();
            int Y = Reader.getPositionY();
            int Width = Reader.getSizeX();
            int Height = Reader.getSizeY();
            
            if (X+Width > maxX) {
                maxX = X+Width;
            }
            
            if (X < minX) {
                minX = X;
            }
            
            if (Y+Height > maxY) {
                maxY = Y+Height;
            }
            
            if (Y < minY) {
                minY = Y;
            }
        }
        
        this.ImageOrigin = new int[]{minX, minY};
        this.ImageSize = new int[]{maxX-minX, maxY-minY};
        
        this.MaxResolution = maxRes;
    }
    
    public CZIReader(CZIReader reader) throws FormatException, IOException {
        DebugTools.enableLogging("ERROR");
        this.FileName = reader.FileName();
        this.SceneCount = reader.nScenes();
        this.ImageSize = reader.getImageSize();
        this.ImageOrigin = reader.getImageOrigin();
        this.MaxResolution = reader.nResolutions();
        this.BarcodeIndex = reader.BarcodeIndex();
        this.PreviewIndex = reader.PreviewIndex();
        
        this.Reader = new ZeissCZIReaderX();
        this.Reader.setFlattenedResolutions(false);
        this.Reader.setId(this.FileName);
    }
    
    protected int[] getImageOrigin() {
        return new int[]{ImageOrigin[0], ImageOrigin[1]};
    }
    
    protected int BarcodeIndex() {
        return BarcodeIndex;
    }
    
    protected int PreviewIndex() {
        return PreviewIndex;
    }
    
    public String FileName() {
        return FileName;
    }
    
    public int[] getImageSize() {
        return new int[]{ImageSize[0], ImageSize[1]};
    }
    
    public int[] getImageSize(int resolution) {
        return new int[]{ImageSize[0]/(int)Math.pow(getDownsampleFactor(), resolution), ImageSize[1]/(int)Math.pow(getDownsampleFactor(), resolution)};
    }
       
    public byte[] getImageRegion() throws FormatException, IOException {
        return getImageRegion(0);
    }
    
    public byte[] getImageRegion(int resolution) throws FormatException, IOException {
        return getImageRegion(resolution, 0, 0, ImageSize[0]/(int)Math.pow(getDownsampleFactor(), resolution), ImageSize[1]/(int)Math.pow(getDownsampleFactor(), resolution));
    }
    
    public byte[] getImageRegion(int x, int y, int width, int height) throws FormatException, IOException {
        return getImageRegion(0, x, y, width, height);
    }
    
    public byte[] getImageRegion(int resolution, int x, int y, int width, int height) throws FormatException, IOException {
        int bpp = imageBPP();
        
        if (bpp <= 8) {
            return getImageRegion8(resolution, x, y, width, height);
        } else if (bpp <= 16) {
            return getImageRegion16(resolution, x, y, width, height);
        } else {
            return getImageRegionX(resolution, x, y, width, height);
        }
    }
    
    private byte[] getImageRegion8(int resolution, int x, int y, int width, int height) throws FormatException, IOException {
        byte maxByte = (byte)0xFF;
        int nChannels = imageChannels();
        
        double dataTest = (Math.log(width)+Math.log(height)+Math.log(nChannels))/Math.log(2);
        
        if (dataTest+0.000001 >= 31) {
            throw new FormatException("Selected image region ("+width+" X "+height+") is too large.");
        }
        
        int xEnd = x+width;
        int yEnd = y+height;
        
        int[] data = new int[width*height*nChannels];
        int[] dataCount = new int[data.length];
        
        for (int i=0; i<SceneCount; i++) {
            int sceneLowestResolution = nResolutions(i);
            
            int sceneResolution = Math.min(resolution, sceneLowestResolution-1);
            int scale = (int)Math.pow(getDownsampleFactor(), resolution-sceneResolution);
            
            int[] scenePosition = getScenePosition(i, sceneResolution);
            int[] sceneSize = getSceneSize(i, sceneResolution);
            int[] scenePositionEnd = new int[]{scenePosition[0]+sceneSize[0], scenePosition[1]+sceneSize[1]};
            
            int sX = x*scale;
            int sY = y*scale;
            int sXEnd = xEnd*scale;
            int sYEnd = yEnd*scale;
            
            if ((sX < scenePositionEnd[0]) && (sY < scenePositionEnd[1]) && (sXEnd > scenePosition[0]) && (sYEnd > scenePosition[1])) {
                int sceneX = Math.max(sX, scenePosition[0]);
                int sceneY = Math.max(sY, scenePosition[1]);
                int sceneXEnd = Math.min(sXEnd, scenePositionEnd[0]);
                int sceneYEnd = Math.min(sYEnd, scenePositionEnd[1]);
                
                int sceneWidth = sceneXEnd-sceneX;
                int sceneHeight = sceneYEnd-sceneY;
                sceneX = sceneX - scenePosition[0];
                sceneY = sceneY - scenePosition[1];
                
                byte[] sceneData = getSceneImage(i, sceneResolution, sceneX, sceneY, sceneWidth, sceneHeight);
                
                for (int j=0; j<sceneData.length; j=j+nChannels) {
                    if (!(sceneData[j]==0 && sceneData[j+1]==0 && sceneData[j+2]==0) && !(sceneData[j]==maxByte && sceneData[j+1]==maxByte && sceneData[j+2]==maxByte)) {
                        int xOffset = (j/nChannels)%sceneWidth;
                        int yOffset = (j/nChannels)/sceneWidth;
                    
                        int idx = (((scenePosition[0]+sceneX+xOffset)/scale-x)+(((scenePosition[1]+sceneY+yOffset)/scale-y)*width))*nChannels;
                        
                        for (int k=0; k<nChannels; k++) {
                            dataCount[idx+nChannels-k-1]++;
                            data[idx+nChannels-k-1] = data[idx+nChannels-k-1] + (sceneData[j+k] & 0xFF);
                        }
                    }
                }
            }
        }
        
        byte[] dataB = new byte[data.length];
        
        for (int i=0; i<data.length; i++) {
            if (dataCount[i] == 0) {
                dataB[i] = maxByte;
            } else {
                dataB[i] = (byte)Math.round((double)data[i]/(double)dataCount[i]);
            }
        }
        
        return dataB;
    }
    
    private byte[] getImageRegion16(int resolution, int x, int y, int width, int height) throws FormatException, IOException {
        byte maxByte = (byte)0xFF;
        int nChannels = imageChannels();
        int bytesPerColourPixel = nChannels*2;
        
        double dataTest = (Math.log(width)+Math.log(height)+Math.log(bytesPerColourPixel))/Math.log(2);
        
        if (dataTest+0.000001 >= 31) {
            throw new FormatException("Selected image region ("+width+" X "+height+") is too large.");
        }
        
        int xEnd = x+width;
        int yEnd = y+height;
        
        int[] data = new int[width*height*nChannels];
        int[] dataCount = new int[data.length];
        
        for (int i=0; i<SceneCount; i++) {
            int sceneLowestResolution = nResolutions(i);
            
            int sceneResolution = Math.min(resolution, sceneLowestResolution-1);
            int scale = (int)Math.pow(getDownsampleFactor(), resolution-sceneResolution);
            
            int[] scenePosition = getScenePosition(i, sceneResolution);
            int[] sceneSize = getSceneSize(i, sceneResolution);
            int[] scenePositionEnd = new int[]{scenePosition[0]+sceneSize[0], scenePosition[1]+sceneSize[1]};
            
            int sX = x*scale;
            int sY = y*scale;
            int sXEnd = xEnd*scale;
            int sYEnd = yEnd*scale;
            
            if ((sX < scenePositionEnd[0]) && (sY < scenePositionEnd[1]) && (sXEnd > scenePosition[0]) && (sYEnd > scenePosition[1])) {
                int sceneX = Math.max(sX, scenePosition[0]);
                int sceneY = Math.max(sY, scenePosition[1]);
                int sceneXEnd = Math.min(sXEnd, scenePositionEnd[0]);
                int sceneYEnd = Math.min(sYEnd, scenePositionEnd[1]);
                
                int sceneWidth = sceneXEnd-sceneX;
                int sceneHeight = sceneYEnd-sceneY;
                sceneX = sceneX - scenePosition[0];
                sceneY = sceneY - scenePosition[1];
                
                byte[] sceneData = getSceneImage(i, sceneResolution, sceneX, sceneY, sceneWidth, sceneHeight);
                
                for (int j=0; j<sceneData.length; j=j+bytesPerColourPixel) {
                    boolean pixelCheck1 = (sceneData[j]==0 && sceneData[j+1]==0 && sceneData[j+2]==0 && sceneData[j+3]==0 && sceneData[j+4]==0 && sceneData[j+5]==0);
                    boolean pixelCheck2 = (sceneData[j]==maxByte && sceneData[j+1]==maxByte && sceneData[j+2]==maxByte && sceneData[j+3]==maxByte && sceneData[j+4]==maxByte && sceneData[j+5]==maxByte);
                    
                    if (!pixelCheck1 && !pixelCheck2) {
                        int xOffset = (j/bytesPerColourPixel)%sceneWidth;
                        int yOffset = (j/bytesPerColourPixel)/sceneWidth;
                    
                        int idx = (((scenePosition[0]+sceneX+xOffset)/scale-x)+(((scenePosition[1]+sceneY+yOffset)/scale-y)*width))*nChannels;
                        
                        for (int k=0; k<nChannels; k++) {
                            dataCount[idx+nChannels-k-1]++;
                            data[idx+nChannels-k-1] = data[idx+nChannels-k-1] + (sceneData[j+(2*k)] & 0xFF) + 0x100*(sceneData[j+(2*k)+1] & 0xFF);
                        }
                    }
                }
            }
        }
        
        byte[] dataB = new byte[data.length*2];
        
        for (int i=0; i<data.length; i++) {
            if (dataCount[i] == 0) {
                dataB[i*2] = maxByte;
                dataB[(i*2)+1] = maxByte;
            } else { 
                double value = Math.round((double)data[i]/(double)dataCount[i]);
                
                dataB[i*2] = (byte)(value%0x100);
                dataB[(i*2)+1] = (byte)(value/0x100);
            }
        }
        
        return dataB;
    }
    
    private byte[] getImageRegionX(int resolution, int x, int y, int width, int height) throws FormatException, IOException {
        byte maxByte = (byte)0xFF;
        int nChannels = imageChannels();
        
        int bytesPerPixel = ((imageBPP()+7)/8);
        int bytesPerColourPixel = bytesPerPixel*nChannels;
        
        double dataTest = (Math.log(width)+Math.log(height)+Math.log(bytesPerColourPixel))/Math.log(2);
        
        if (dataTest+0.000001 >= 31) {
            throw new FormatException("Selected image region ("+width+" X "+height+") is too large.");
        }
        
        int xEnd = x+width;
        int yEnd = y+height;
        
        double[] data = new double[width*height*nChannels];
        double[] dataCount = new double[data.length];
        
        for (int i=0; i<SceneCount; i++) {
            int sceneLowestResolution = nResolutions(i);
            
            int sceneResolution = Math.min(resolution, sceneLowestResolution-1);
            int scale = (int)Math.pow(getDownsampleFactor(), resolution-sceneResolution);
            
            int[] scenePosition = getScenePosition(i, sceneResolution);
            int[] sceneSize = getSceneSize(i, sceneResolution);
            int[] scenePositionEnd = new int[]{scenePosition[0]+sceneSize[0], scenePosition[1]+sceneSize[1]};
            
            int sX = x*scale;
            int sY = y*scale;
            int sXEnd = xEnd*scale;
            int sYEnd = yEnd*scale;
            
            if ((sX < scenePositionEnd[0]) && (sY < scenePositionEnd[1]) && (sXEnd > scenePosition[0]) && (sYEnd > scenePosition[1])) {
                int sceneX = Math.max(sX, scenePosition[0]);
                int sceneY = Math.max(sY, scenePosition[1]);
                int sceneXEnd = Math.min(sXEnd, scenePositionEnd[0]);
                int sceneYEnd = Math.min(sYEnd, scenePositionEnd[1]);
                
                int sceneWidth = sceneXEnd-sceneX;
                int sceneHeight = sceneYEnd-sceneY;
                sceneX = sceneX - scenePosition[0];
                sceneY = sceneY - scenePosition[1];
                
                byte[] sceneData = getSceneImage(i, sceneResolution, sceneX, sceneY, sceneWidth, sceneHeight);
                
                for (int j=0; j<sceneData.length; j=j+bytesPerColourPixel) {
                    boolean pixelCheck1 = true;
                    
                    for (int k=0; k<bytesPerPixel && pixelCheck1; k++) {
                        pixelCheck1 = (sceneData[j+k] == 0);
                    }
                    
                    if (!pixelCheck1) {
                        boolean pixelCheck2 = true;
                    
                        for (int k=0; k<bytesPerPixel && pixelCheck2; k++) {
                            pixelCheck2 = (sceneData[j+k] == maxByte);
                        }
                    
                        if (!pixelCheck2) {
                            int xOffset = (j/bytesPerColourPixel)%sceneWidth;
                            int yOffset = (j/bytesPerColourPixel)/sceneWidth;
                    
                            int idx = (((scenePosition[0]+sceneX+xOffset)/scale-x)+(((scenePosition[1]+sceneY+yOffset)/scale-y)*width))*nChannels;
                        
                            for (int k=0; k<nChannels; k++) {
                                double value = 0;
                            
                                for (int l=bytesPerPixel-1; l>=0; l--) {
                                    value = value * 0x100;
                                    value = value + (sceneData[j+(k*bytesPerPixel)+l] & 0xFF);
                                }
                        
                                dataCount[idx+nChannels-k-1]++;
                                data[idx+nChannels-k-1] = data[idx+nChannels-k-1] + value;
                            }
                        }
                    }
                }
            }
        }
        
        byte[] dataB = new byte[data.length*bytesPerPixel];
        
        for (int i=0; i<data.length; i++) {
            if (dataCount[i] == 0) {
                for (int j=0; j<bytesPerPixel; j++) {
                    dataB[(i*bytesPerPixel)+j] = maxByte;
                } 
            } else { 
                double value = Math.round(data[i]/dataCount[i]);
                
                for (int j=0; j<bytesPerPixel; j++) {
                    dataB[(i*bytesPerPixel)+j] = (byte)(value%0x100);
                    value = value/0x100;
                }
            }
        }
        
        return dataB;
    }
    
    public int[] getScenePosition(int scene) {
        return getScenePosition(scene, 0);
    }
    
    public int[] getScenePosition(int scene, int resolution) {
        Reader.setSeries(scene);
        Reader.setResolution(resolution);
        return new int[]{Reader.getPositionX()-(ImageOrigin[0]/(int)Math.pow(getDownsampleFactor(), resolution)), Reader.getPositionY()-(ImageOrigin[1]/(int)Math.pow(getDownsampleFactor(), resolution))};
    }
   
    public int[] getSceneSize(int scene) {
        return getSceneSize(scene, 0);
    }
    
    public int[] getSceneSize(int scene, int resolution) {
        Reader.setSeries(scene);
        Reader.setResolution(resolution);
        return new int[]{Reader.getSizeX(), Reader.getSizeY()};
    }

    public byte[] getSceneImage(int scene) throws FormatException, IOException {
        return getSceneImage(scene, 0);
    }
    
    public byte[] getSceneImage(int scene, int resolution) throws FormatException, IOException {
        int[] size = getSceneSize(scene, resolution);
        return getSceneImage(scene, resolution, 0, 0, size[0], size[1]);
    }
    
    public byte[] getSceneImage(int scene, int x, int y, int width, int height) throws FormatException, IOException {
        return getSceneImage(scene, 0, x, y, width, height);
    }
    
    public byte[] getSceneImage(int scene, int resolution, int x, int y, int width, int height) throws FormatException, IOException {
        Reader.setSeries(scene);
        Reader.setResolution(resolution);
        return Reader.openBytes(0, x, y, width, height);
    }
    
    public byte[] getBarcodeImage() throws FormatException, IOException {
        if (BarcodeIndex >= 0) {
            int[] barcodeSize = getBarcodeSize();
            return getSceneImage(BarcodeIndex, 0, 0, 0, barcodeSize[0], barcodeSize[1]);
        } else {
            return null;
        }
    }
    
    public int[] getBarcodeSize() {
        if (BarcodeIndex >= 0) {
            return getSceneSize(BarcodeIndex, 0);
        } else {
            return null;
        }
    }
    
    public byte[] getPreviewImage() throws FormatException, IOException {
        if (PreviewIndex >= 0) {
            int[] previewSize = getPreviewSize();
            return getSceneImage(PreviewIndex, 0, 0, 0, previewSize[0], previewSize[1]);
        } else {
            return null;
        }
    }
    
    public int[] getPreviewSize() {
        if (PreviewIndex >= 0) {
            return getSceneSize(PreviewIndex, 0);
        } else {
            return null;
        }
    }

    public int getDownsampleFactor() {
        return Reader.getDownsampleFactor();
    }

    public int imageBPP() {
        this.Reader.setSeries(0);
        this.Reader.setResolution(0);
        return Reader.getBitsPerPixel();
    }
    
    public int imageChannels() {
        this.Reader.setSeries(0);
        this.Reader.setResolution(0);
        return Reader.getRGBChannelCount();
    }
    
    public int barcodeBPP() {
        if (BarcodeIndex >= 0) {
            Reader.setSeries(BarcodeIndex);
            return Reader.getBitsPerPixel();
        } else {
            return 0;
        }
    }
    
    public int barcodeChannels() {
        if (BarcodeIndex >= 0) {
            Reader.setSeries(BarcodeIndex);
            return this.Reader.getRGBChannelCount();
        } else {
            return 0;
        }
    }
    
    public int previewBPP() {
        if (PreviewIndex >= 0) {
            Reader.setSeries(PreviewIndex);
            return Reader.getBitsPerPixel();
        } else {
            return 0;
        }
    }
    
    public int previewChannels() {
        if (PreviewIndex >= 0) {
            Reader.setSeries(PreviewIndex);
            return this.Reader.getRGBChannelCount();
        } else {
            return 0;
        }
    }
    
    public int nResolutions() {
        return MaxResolution;
    }
    
    public int nResolutions(int scene) {
        Reader.setSeries(scene);
        return Reader.getResolutionCount();
    }

    public int nScenes() {
        return SceneCount;
    }
    
    public void close() throws IOException {
        Reader.close();
    }
}